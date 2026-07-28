package dev.dimi.erp.movimentacao.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do fluxo end-to-end do slice 5.2: pedido → aprovar → converter → criar venda direta → fechar
 * (consumindo estoque). Valida que {@code close} baixa estoque via {@code StockInventoryPort.ship}
 * e que {@code Idempotency-Key} é obrigatório no POST de venda.
 */
class SaleFlowIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CPF_VALIDO = "11144477735";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;
  private String branchId;
  private String customerId;
  private String packagingId;

  @BeforeEach
  void setUp() throws Exception {
    tenantSlug = "mov-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Mov IT Co",
          "legalName": "Mov IT Limitada",
          "cnpj": "%s",
          "plan": "free",
          "ownerUsername": "owner",
          "ownerEmail": "owner@example.com",
          "ownerFullName": "Owner User"
        }
        """
            .formatted(tenantSlug, randomCnpj());

    MvcResult prov =
        mockMvc
            .perform(
                post("/api/v1/platform/tenants")
                    .header("X-Bootstrap-Token", BOOTSTRAP_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    temporaryPassword =
        objectMapper
            .readTree(prov.getResponse().getContentAsString())
            .get("temporaryPassword")
            .asText();
    accessToken = doLogin("owner", temporaryPassword).accessToken();

    MvcResult branches =
        mockMvc
            .perform(
                get("/api/v1/cadastros/branches").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode page = objectMapper.readTree(branches.getResponse().getContentAsString());
    branchId = page.get("content").get(0).get("id").asText();

    customerId = createCustomer();
    packagingId = createPackaging("MOV-A");
    createDefaultLocation();
    seedInitialStock(packagingId, 50); // 50 unidades para conseguir fechar venda
  }

  @Test
  @DisplayName("POST /sales sem Idempotency-Key devolve 400")
  void semIdempotencyKey_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/movimentacao/sales")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "branchId":"%s",
                      "customerId":"%s",
                      "items":[{"packagingId":"%s","quantity":1,"unitPrice":10.00}]
                    }
                    """
                        .formatted(branchId, customerId, packagingId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("movimentacao.invalid-sale-operation"));
  }

  @Test
  @DisplayName("fluxo completo: criar pedido → submit → approve → convert → close → saldo cai")
  void fluxoCompleto() throws Exception {
    // 1. Cria pedido com 2 itens
    String orderId = createOrder(2, "10.00");

    mockMvc
        .perform(
            get("/api/v1/movimentacao/sales-orders/" + orderId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("DRAFT"))
        .andExpect(jsonPath("$.total").value(20.00));

    // 2. Submete e aprova
    mockMvc
        .perform(
            post("/api/v1/movimentacao/sales-orders/" + orderId + "/submit")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));
    mockMvc
        .perform(
            post("/api/v1/movimentacao/sales-orders/" + orderId + "/approve")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("APPROVED"));

    // 3. Converte em venda
    MvcResult conv =
        mockMvc
            .perform(
                post("/api/v1/movimentacao/sales-orders/" + orderId + "/convert")
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andReturn();
    String saleId =
        objectMapper.readTree(conv.getResponse().getContentAsString()).get("id").asText();

    // 4. Fecha venda — baixa estoque via StockInventoryPort.ship
    mockMvc
        .perform(
            post("/api/v1/movimentacao/sales/" + saleId + "/close")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"))
        .andExpect(jsonPath("$.closedAt").exists());

    // 5. Saldo de estoque caiu (50 - 2 = 48)
    MvcResult snap =
        mockMvc
            .perform(
                get("/api/v1/estoque/snapshots")
                    .param("packagingId", packagingId)
                    .param("branchId", branchId)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode arr = objectMapper.readTree(snap.getResponse().getContentAsString());
    assertThat(arr.get(0).get("quantityOnHand").asDouble()).isEqualTo(48.0);
  }

  @Test
  @DisplayName("POST /sales com mesma Idempotency-Key devolve a mesma venda (RN-MOV-004)")
  void idempotenciaVenda() throws Exception {
    String key = "sale:dup:" + System.nanoTime();
    String saleBody =
        """
        {
          "branchId":"%s",
          "customerId":"%s",
          "items":[{"packagingId":"%s","quantity":1,"unitPrice":10.00}]
        }
        """
            .formatted(branchId, customerId, packagingId);

    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/movimentacao/sales")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(saleBody))
            .andExpect(status().isCreated())
            .andReturn();
    String firstId =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

    MvcResult second =
        mockMvc
            .perform(
                post("/api/v1/movimentacao/sales")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(saleBody))
            .andExpect(status().isCreated())
            .andReturn();
    String secondId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

    assertThat(secondId).isEqualTo(firstId);
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String createCustomer() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/persons")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "type": "PF",
                          "legalName": "Cliente Teste",
                          "cpf": "%s",
                          "modalities": ["CUSTOMER"]
                        }
                        """
                            .formatted(CPF_VALIDO)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private String createPackaging(String skuRoot) throws Exception {
    MvcResult prod =
        mockMvc
            .perform(
                post("/api/v1/produtos")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "skuRoot":"%s",
                          "name":"Produto %s",
                          "classification":"PRODUTO",
                          "ncm":"61091000"
                        }
                        """
                            .formatted(skuRoot, skuRoot)))
            .andExpect(status().isCreated())
            .andReturn();
    String productId =
        objectMapper.readTree(prod.getResponse().getContentAsString()).get("id").asText();

    MvcResult variantRes =
        mockMvc
            .perform(
                post("/api/v1/produtos/" + productId + "/variants")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"MAIN\",\"name\":\"Main\",\"asDefault\":false}"))
            .andExpect(status().isCreated())
            .andReturn();
    String variantId =
        objectMapper.readTree(variantRes.getResponse().getContentAsString()).get("id").asText();

    MvcResult pkgRes =
        mockMvc
            .perform(
                post("/api/v1/produtos/" + productId + "/variants/" + variantId + "/packagings")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"sku":"%s-PKG","asDefault":false,"conversionFactor":1.0}
                        """
                            .formatted(skuRoot)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(pkgRes.getResponse().getContentAsString()).get("id").asText();
  }

  private void createDefaultLocation() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/estoque/locations")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"branchId":"%s","code":"DEP-01","name":"Depósito Principal","asDefault":true}
                    """
                        .formatted(branchId)))
        .andExpect(status().isCreated());
  }

  /** Sobe saldo inicial via /adjustments + step-up. */
  private void seedInitialStock(String packagingId, int qty) throws Exception {
    MvcResult locs =
        mockMvc
            .perform(
                get("/api/v1/estoque/locations")
                    .param("branchId", branchId)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    String locationId =
        objectMapper.readTree(locs.getResponse().getContentAsString()).get(0).get("id").asText();

    String steppedToken = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/estoque/adjustments")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "packagingId":"%s",
                      "branchId":"%s",
                      "locationId":"%s",
                      "delta":%d,
                      "reason":"seed inicial para IT"
                    }
                    """
                        .formatted(packagingId, branchId, locationId, qty)))
        .andExpect(status().isCreated());
  }

  private String createOrder(int qty, String unitPrice) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/movimentacao/sales-orders")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "branchId":"%s",
                          "customerId":"%s",
                          "items":[
                            {"packagingId":"%s","quantity":%d,"unitPrice":%s}
                          ]
                        }
                        """
                            .formatted(branchId, customerId, packagingId, qty, unitPrice)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private String doStepUp() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/auth/step-up")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"" + temporaryPassword + "\"}"))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper
        .readTree(res.getResponse().getContentAsString())
        .get("accessToken")
        .asText();
  }

  private LoginApiResponse doLogin(String identifier, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"tenantSlug":"%s","identifier":"%s","password":"%s"}
                        """
                            .formatted(tenantSlug, identifier, password)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readValue(
        result.getResponse().getContentAsString(), LoginApiResponse.class);
  }
}
