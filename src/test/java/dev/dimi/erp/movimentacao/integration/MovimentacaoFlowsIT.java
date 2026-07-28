package dev.dimi.erp.movimentacao.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do slice 5.3: 1 cenário por fluxo (Return, Transfer-rejeição por origem=destino, Voucher
 * step-up, Adjustment com delta). Cenários full-stack via Testcontainers + Postgres.
 */
class MovimentacaoFlowsIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CPF_VALIDO = "11144477735";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;
  private String branchId;
  private String customerId;
  private String packagingId;
  private String locationId;

  @BeforeEach
  void setUp() throws Exception {
    tenantSlug = "mov53-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Mov 5.3 IT Co",
          "legalName": "Mov 5.3 IT Limitada",
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
    packagingId = createPackaging("MOV53-A");
    locationId = createDefaultLocation();
    seedStock(packagingId, 50);
  }

  // --------------------------------------------------------------------------
  // RETURN — fluxo aberto+fechado devolve estoque
  // --------------------------------------------------------------------------

  @Test
  @DisplayName("devolução: abre venda → fecha → abre return com 3 returned → close → estoque sobe")
  void return_fluxo() throws Exception {
    String saleId = openAndCloseSale(packagingId, 5, "10.00");

    // Lê o sale_item id do response
    MvcResult sr =
        mockMvc
            .perform(
                get("/api/v1/movimentacao/sales/" + saleId)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    String saleItemId =
        objectMapper
            .readTree(sr.getResponse().getContentAsString())
            .get("items")
            .get(0)
            .get("id")
            .asText();

    // Saldo após fechar venda: 50 - 5 = 45
    assertSnapshotOnHand(45.0);

    // Abre devolução com 3 returned (devolve estoque) + 1 lost (refund mas sem estoque)
    MvcResult open =
        mockMvc
            .perform(
                post("/api/v1/movimentacao/returns")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "saleId":"%s",
                          "reason":"cliente desistiu",
                          "items":[
                            {"saleItemId":"%s","quantityReturned":3,"quantityLost":1,"quantityUsed":0}
                          ]
                        }
                        """
                            .formatted(saleId, saleItemId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.total").value(40.00)) // (3+1) * 10.00
            .andReturn();
    String returnId =
        objectMapper.readTree(open.getResponse().getContentAsString()).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/movimentacao/returns/" + returnId + "/close")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CLOSED"));

    // Saldo final: 45 + 3 = 48 (lost não volta)
    assertSnapshotOnHand(48.0);
  }

  // --------------------------------------------------------------------------
  // TRANSFER — origem=destino rejeitada
  // --------------------------------------------------------------------------

  @Test
  @DisplayName("transferência com origem=destino devolve 400 same-origin-dest")
  void transfer_origemIgualDestino_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/movimentacao/transfers")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "originBranchId":"%s",
                      "destBranchId":"%s",
                      "originLocationId":"%s",
                      "destLocationId":"%s",
                      "items":[{"packagingId":"%s","quantity":1}]
                    }
                    """
                        .formatted(branchId, branchId, locationId, locationId, packagingId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("movimentacao.invalid-sale-operation"))
        .andExpect(jsonPath("$.reason").value("same-origin-dest"));
  }

  // --------------------------------------------------------------------------
  // VOUCHER — step-up exigido
  // --------------------------------------------------------------------------

  @Test
  @DisplayName("saída avulsa sem step-up devolve 401 com WWW-Authenticate StepUp")
  void voucher_semStepUp_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/movimentacao/outbound-vouchers")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "branchId":"%s",
                      "reason":"SAMPLE",
                      "items":[{"packagingId":"%s","quantity":1}]
                    }
                    """
                        .formatted(branchId, packagingId)))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("StepUp")));
  }

  @Test
  @DisplayName("saída avulsa com step-up baixa estoque")
  void voucher_comStepUp_baixaEstoque() throws Exception {
    String steppedToken = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/movimentacao/outbound-vouchers")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "branchId":"%s",
                      "reason":"SAMPLE",
                      "notes":"amostra para cliente VIP",
                      "items":[{"packagingId":"%s","quantity":5}]
                    }
                    """
                        .formatted(branchId, packagingId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.reason").value("SAMPLE"));
    assertSnapshotOnHand(45.0); // 50 - 5
  }

  // --------------------------------------------------------------------------
  // ADJUSTMENT — delta dispara movimento via StockInventoryPort.adjust
  // --------------------------------------------------------------------------

  @Test
  @DisplayName("ajuste de inventário com counted < expected baixa saldo")
  void adjustment_deltaNegativo() throws Exception {
    String steppedToken = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/movimentacao/inventory-adjustments")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "branchId":"%s",
                      "reason":"conferência mensal",
                      "items":[
                        {"packagingId":"%s","locationId":"%s","expectedQty":50,"countedQty":48}
                      ]
                    }
                    """
                        .formatted(branchId, packagingId, locationId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.items[0].delta").value(-2.0));
    assertSnapshotOnHand(48.0); // 50 + (48-50) = 48
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private void assertSnapshotOnHand(double expected) throws Exception {
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
    assertThat(arr.get(0).get("quantityOnHand").asDouble()).isEqualTo(expected);
  }

  private String openAndCloseSale(String packagingId, int qty, String unitPrice) throws Exception {
    String key = "sale:" + UUID.randomUUID();
    MvcResult sale =
        mockMvc
            .perform(
                post("/api/v1/movimentacao/sales")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", key)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "branchId":"%s",
                          "customerId":"%s",
                          "items":[{"packagingId":"%s","quantity":%d,"unitPrice":%s}]
                        }
                        """
                            .formatted(branchId, customerId, packagingId, qty, unitPrice)))
            .andExpect(status().isCreated())
            .andReturn();
    String saleId =
        objectMapper.readTree(sale.getResponse().getContentAsString()).get("id").asText();
    mockMvc
        .perform(
            post("/api/v1/movimentacao/sales/" + saleId + "/close")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
    return saleId;
  }

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
                          "legalName": "Cliente",
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
    MvcResult variant =
        mockMvc
            .perform(
                post("/api/v1/produtos/" + productId + "/variants")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"code\":\"MAIN\",\"name\":\"Main\",\"asDefault\":false}"))
            .andExpect(status().isCreated())
            .andReturn();
    String variantId =
        objectMapper.readTree(variant.getResponse().getContentAsString()).get("id").asText();
    MvcResult pkg =
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
    return objectMapper.readTree(pkg.getResponse().getContentAsString()).get("id").asText();
  }

  private String createDefaultLocation() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/estoque/locations")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"branchId":"%s","code":"DEP-01","name":"Depósito","asDefault":true}
                        """
                            .formatted(branchId)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private void seedStock(String packagingId, int qty) throws Exception {
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
                      "reason":"seed inicial"
                    }
                    """
                        .formatted(packagingId, branchId, locationId, qty)))
        .andExpect(status().isCreated());
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
