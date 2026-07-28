package dev.dimi.erp.compras.integration;

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
 * IT do fluxo end-to-end do slice 6.2: SC → submit → approve → cotação → invite + responses → close
 * → gera PCs. Valida que PC gerado tem itens vindos das respostas selecionadas e que a SC vai para
 * ORDERED.
 */
class PurchaseFlowIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CNPJ_FORNECEDOR = "11222333000181";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;
  private String branchId;
  private String supplierId;
  private String packagingId;

  @BeforeEach
  void setUp() throws Exception {
    tenantSlug = "compras-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Compras IT Co",
          "legalName": "Compras IT Limitada",
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

    supplierId = createSupplier("Fornecedor ABC", CNPJ_FORNECEDOR);
    packagingId = createPackaging("COM-A");
  }

  @Test
  @DisplayName("fluxo completo: SC → submit → approve → cotação → invite + response → close → PC")
  void fluxoCompleto() throws Exception {
    String requestId = createRequest(packagingId, 10, "5.00");

    mockMvc
        .perform(
            post("/api/v1/compras/requests/" + requestId + "/submit")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("PENDING_APPROVAL"));

    mockMvc
        .perform(
            post("/api/v1/compras/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("APPROVED"));

    String quotationId = openQuotation(requestId);
    inviteSupplier(quotationId, supplierId);
    JsonNode quotationAfterResponse = registerResponse(quotationId, supplierId, packagingId);
    String responseLineId = quotationAfterResponse.get("responses").get(0).get("id").asText();

    mockMvc
        .perform(
            post("/api/v1/compras/quotations/" + quotationId + "/close")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("CLOSED"));

    // Após close, SC permanece QUOTING — só vai para ORDERED quando from-quotation gera PCs.
    mockMvc
        .perform(
            get("/api/v1/compras/requests/" + requestId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("QUOTING"));

    MvcResult orders =
        mockMvc
            .perform(
                post("/api/v1/compras/orders/from-quotation/" + quotationId)
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "selections":[
                            {
                              "supplierId":"%s",
                              "selectedResponseIds":["%s"]
                            }
                          ]
                        }
                        """
                            .formatted(supplierId, responseLineId)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode arr = objectMapper.readTree(orders.getResponse().getContentAsString());
    assertThat(arr).hasSize(1);
    String orderId = arr.get(0).get("id").asText();
    assertThat(arr.get(0).get("status").asText()).isEqualTo("OPEN");
    assertThat(arr.get(0).get("total").asDouble()).isEqualTo(50.00);

    // Agora sim ORDERED — generateFromQuotation transiciona a SC.
    mockMvc
        .perform(
            get("/api/v1/compras/requests/" + requestId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("ORDERED"));

    mockMvc
        .perform(
            post("/api/v1/compras/orders/" + orderId + "/send")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("SENT"));
  }

  @Test
  @DisplayName("PC ad-hoc sem cotação cria PC OPEN com totais calculados")
  void pedidoAdHoc() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/compras/orders")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "branchId":"%s",
                          "supplierId":"%s",
                          "totalFreight":10.00,
                          "items":[
                            {"packagingId":"%s","quantity":2,"unitPrice":15.00,"discountValue":0}
                          ]
                        }
                        """
                            .formatted(branchId, supplierId, packagingId)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.totalProducts").value(30.00))
            .andExpect(jsonPath("$.totalFreight").value(10.00))
            .andExpect(jsonPath("$.total").value(40.00))
            .andReturn();
    assertThat(res.getResponse().getStatus()).isEqualTo(201);
  }

  @Test
  @DisplayName("aprovar SC em status diferente de PENDING_APPROVAL/DRAFT devolve 409")
  void approveJaAprovada_409() throws Exception {
    String requestId = createRequest(packagingId, 1, "10.00");
    mockMvc
        .perform(
            post("/api/v1/compras/requests/" + requestId + "/submit")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/compras/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
    mockMvc
        .perform(
            post("/api/v1/compras/requests/" + requestId + "/approve")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("compras.invalid-state"));
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String createSupplier(String legalName, String cnpj) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/cadastros/persons")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "type": "PJ",
                          "legalName": "%s",
                          "cnpj": "%s",
                          "modalities": ["SUPPLIER"]
                        }
                        """
                            .formatted(legalName, cnpj)))
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

  private String createRequest(String packagingId, int qty, String unitCost) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/compras/requests")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "branchId":"%s",
                          "items":[
                            {
                              "packagingId":"%s",
                              "description":"Insumo X",
                              "quantity":%d,
                              "estimatedUnitCost":%s
                            }
                          ]
                        }
                        """
                            .formatted(branchId, packagingId, qty, unitCost)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private String openQuotation(String requestId) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/compras/quotations")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"branchId":"%s","requestId":"%s"}
                        """
                            .formatted(branchId, requestId)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private void inviteSupplier(String quotationId, String supplierId) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/compras/quotations/" + quotationId + "/suppliers")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {"supplierId":"%s","deliveryDays":7,"freightValue":0}
                    """
                        .formatted(supplierId)))
        .andExpect(status().isOk());
  }

  private JsonNode registerResponse(String quotationId, String supplierId, String packagingId)
      throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/compras/quotations/" + quotationId + "/responses")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "supplierId":"%s",
                          "lines":[
                            {
                              "requestItemLine":1,
                              "packagingId":"%s",
                              "description":"Insumo X cotado",
                              "quantity":10,
                              "unitPrice":5.00
                            }
                          ]
                        }
                        """
                            .formatted(supplierId, packagingId)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString());
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
