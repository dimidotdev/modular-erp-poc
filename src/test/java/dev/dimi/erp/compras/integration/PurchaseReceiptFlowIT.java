package dev.dimi.erp.compras.integration;

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
 * IT do slice 6.3: cria PC ad-hoc → envia → recebe (INBOUND no estoque) → devolve a fornecedor
 * (OUTBOUND). Valida idempotência por Idempotency-Key, transição OPEN→SENT→RECEIVED, devolução com
 * step-up e bloqueio de devolução acima do recebido.
 */
class PurchaseReceiptFlowIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CNPJ_FORNECEDOR = "11222333000181";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;
  private String branchId;
  private String supplierId;
  private String packagingId;
  private String orderId;
  private String orderItemId;

  @BeforeEach
  void setUp() throws Exception {
    tenantSlug = "rcpt-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Recpt IT Co",
          "legalName": "Recpt IT Limitada",
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
    branchId =
        objectMapper
            .readTree(branches.getResponse().getContentAsString())
            .get("content")
            .get(0)
            .get("id")
            .asText();

    supplierId = createSupplier();
    packagingId = createPackaging("RCPT-A");
    createDefaultLocation();

    JsonNode order = createOrderAdHocAndSend();
    orderId = order.get("id").asText();
    orderItemId = order.get("items").get(0).get("id").asText();
  }

  @Test
  @DisplayName("POST /receive sem Idempotency-Key → 400")
  void semIdempotencyKey_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/compras/orders/" + orderId + "/receive")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiveBody(5, "10.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("compras.invalid-operation"));
  }

  @Test
  @DisplayName("recebimento total → PC RECEIVED + saldo de estoque sobe")
  void recebimentoTotal_OK() throws Exception {
    String idemKey = "rcpt:" + System.nanoTime();
    mockMvc
        .perform(
            post("/api/v1/compras/orders/" + orderId + "/receive")
                .header("Authorization", "Bearer " + accessToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiveBody(10, "10.00")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.total").value(100.00));

    mockMvc
        .perform(
            get("/api/v1/compras/orders/" + orderId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("RECEIVED"));

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
    org.assertj.core.api.Assertions.assertThat(arr.get(0).get("quantityOnHand").asDouble())
        .isEqualTo(10.0);
  }

  @Test
  @DisplayName("mesma Idempotency-Key → mesmo recebimento (RN-COM-014)")
  void idempotencia() throws Exception {
    String idemKey = "rcpt:dup:" + System.nanoTime();
    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/compras/orders/" + orderId + "/receive")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(receiveBody(5, "10.00")))
            .andExpect(status().isCreated())
            .andReturn();
    String firstId =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

    MvcResult second =
        mockMvc
            .perform(
                post("/api/v1/compras/orders/" + orderId + "/receive")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(receiveBody(5, "10.00")))
            .andExpect(status().isCreated())
            .andReturn();
    String secondId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

    org.assertj.core.api.Assertions.assertThat(secondId).isEqualTo(firstId);
  }

  @Test
  @DisplayName("devolução a fornecedor com step-up → OUTBOUND derruba estoque")
  void devolucaoFornecedor() throws Exception {
    // Recebe primeiro
    String idemKey = "rcpt:ret:" + System.nanoTime();
    MvcResult receipt =
        mockMvc
            .perform(
                post("/api/v1/compras/orders/" + orderId + "/receive")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(receiveBody(10, "10.00")))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode receiptNode = objectMapper.readTree(receipt.getResponse().getContentAsString());
    String receiptId = receiptNode.get("id").asText();
    String receiptItemId = receiptNode.get("items").get(0).get("id").asText();

    String steppedToken = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/compras/supplier-returns")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "receiptId":"%s",
                      "reason":"avaria no transporte",
                      "items":[
                        {"receiptItemId":"%s","quantity":3}
                      ]
                    }
                    """
                        .formatted(receiptId, receiptItemId)))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.total").value(30.00));

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
    org.assertj.core.api.Assertions.assertThat(arr.get(0).get("quantityOnHand").asDouble())
        .isEqualTo(7.0);
  }

  @Test
  @DisplayName("devolução com qty > recebido → 400")
  void devolucaoExcedente_400() throws Exception {
    String idemKey = "rcpt:exc:" + System.nanoTime();
    MvcResult receipt =
        mockMvc
            .perform(
                post("/api/v1/compras/orders/" + orderId + "/receive")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(receiveBody(5, "10.00")))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode receiptNode = objectMapper.readTree(receipt.getResponse().getContentAsString());
    String receiptId = receiptNode.get("id").asText();
    String receiptItemId = receiptNode.get("items").get(0).get("id").asText();

    String steppedToken = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/compras/supplier-returns")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "receiptId":"%s",
                      "reason":"avaria",
                      "items":[
                        {"receiptItemId":"%s","quantity":99}
                      ]
                    }
                    """
                        .formatted(receiptId, receiptItemId)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("compras.invalid-operation"));
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String receiveBody(int qty, String unitCost) {
    return """
        {
          "items":[
            {"orderItemId":"%s","quantity":%d,"unitCost":%s}
          ]
        }
        """
        .formatted(orderItemId, qty, unitCost);
  }

  private JsonNode createOrderAdHocAndSend() throws Exception {
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
                          "items":[
                            {"packagingId":"%s","quantity":10,"unitPrice":10.00}
                          ]
                        }
                        """
                            .formatted(branchId, supplierId, packagingId)))
            .andExpect(status().isCreated())
            .andReturn();
    JsonNode order = objectMapper.readTree(res.getResponse().getContentAsString());
    String id = order.get("id").asText();
    mockMvc
        .perform(
            post("/api/v1/compras/orders/" + id + "/send")
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk());
    return order;
  }

  private String createSupplier() throws Exception {
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
                          "legalName": "Fornecedor LTDA",
                          "cnpj": "%s",
                          "modalities": ["SUPPLIER"]
                        }
                        """
                            .formatted(CNPJ_FORNECEDOR)))
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
