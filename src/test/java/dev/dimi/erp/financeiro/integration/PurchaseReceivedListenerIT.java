package dev.dimi.erp.financeiro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do slice 7.3: PurchaseReceivedFinancialListener.
 *
 * <p>Cenário golden: tenant configurado com chart_account expense + cost_center → POST recebimento
 * → assíncrono → MVF DEBIT é criada com origin (PURCHASE, receiptId). Verificação polling com
 * Awaitility (listener é {@code @Async + @TransactionalEventListener AFTER_COMMIT}).
 *
 * <p>Cenário skip: tenant sem default_expense_chart_account_id → recebimento entra, MVF não é
 * criada, sem erro. (Validar log/skip event seria via SkippedEventListener IT — deferido.)
 */
class PurchaseReceivedListenerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CNPJ_SUPPLIER = "11222333000181";

  @Autowired JdbcTemplate jdbc;

  private String tenantSlug;
  private String tenantSchema;
  private String temporaryPassword;
  private String accessToken;
  private String branchId;
  private String supplierId;
  private String packagingId;
  private String orderId;
  private String orderItemId;
  private String chartExpenseId;
  private String costCenterId;

  @BeforeEach
  void setUp() throws Exception {
    tenantSlug = "fin-listener-" + System.nanoTime();
    tenantSchema = "tenant_" + tenantSlug.replace('-', '_');

    String body =
        """
        {
          "slug": "%s",
          "displayName": "Listener IT Co",
          "legalName": "Listener IT Limitada",
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
    packagingId = createPackaging("LST-A");
    createDefaultLocation();

    JsonNode order = createOrderAdHocAndSend();
    orderId = order.get("id").asText();
    orderItemId = order.get("items").get(0).get("id").asText();

    chartExpenseId = insertChartAccount("3.01.001", "Despesa Compras", "EXPENSE");
    costCenterId = insertCostCenter("CC.001", "Centro Geral");
  }

  @Test
  @DisplayName(
      "setting configurada + receive → MVF DEBIT criada async com origin (PURCHASE,receiptId)")
  void recebimento_criaMvfAutomaticamente() throws Exception {
    configureSettings(chartExpenseId, costCenterId);

    String idemKey = "rcpt-listener:" + System.nanoTime();
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
    String receiptId =
        objectMapper.readTree(receipt.getResponse().getContentAsString()).get("id").asText();

    // Listener é assíncrono — espera até ~5s pela MVF aparecer com origin matching.
    await()
        .atMost(Duration.ofSeconds(5))
        .pollInterval(Duration.ofMillis(200))
        .untilAsserted(
            () -> {
              Long count =
                  jdbc.queryForObject(
                      "SELECT count(*) FROM "
                          + tenantSchema
                          + ".financial_record "
                          + "WHERE origin_type = 'PURCHASE' AND origin_id = ?",
                      Long.class,
                      UUID.fromString(receiptId));
              assertThat(count).isEqualTo(1L);
            });

    // Inspeciona a MVF criada via SQL direto (sem endpoint de listing ainda).
    var row =
        jdbc.queryForMap(
            "SELECT direction, total_value, counterparty_id, status FROM "
                + tenantSchema
                + ".financial_record WHERE origin_type = 'PURCHASE' AND origin_id = ?",
            UUID.fromString(receiptId));
    assertThat(row.get("direction")).isEqualTo("DEBIT");
    assertThat(((java.math.BigDecimal) row.get("total_value"))).isEqualByComparingTo("100.00");
    assertThat(row.get("counterparty_id")).isEqualTo(UUID.fromString(supplierId));
    assertThat(row.get("status")).isEqualTo("OPEN");
  }

  @Test
  @DisplayName("setting ausente → MVF não é criada, recebimento ainda passa OK")
  void semSettings_recebimentoOk_semMvf() throws Exception {
    // Nenhuma config — setting permanece com defaults null.
    String idemKey = "rcpt-skip:" + System.nanoTime();
    mockMvc
        .perform(
            post("/api/v1/compras/orders/" + orderId + "/receive")
                .header("Authorization", "Bearer " + accessToken)
                .header("Idempotency-Key", idemKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(receiveBody(10, "10.00")))
        .andExpect(status().isCreated());

    // Aguarda 1.5s para garantir que o listener async já rodou e skipou.
    Thread.sleep(1500);

    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM " + tenantSchema + ".financial_record", Long.class);
    assertThat(count).isZero();
  }

  @Test
  @DisplayName("GET /financeiro/settings retorna singleton com defaults null inicialmente")
  void getSettingsRetornaSingleton() throws Exception {
    mockMvc
        .perform(
            get("/api/v1/financeiro/settings").header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").exists())
        .andExpect(jsonPath("$.defaultRevenueChartAccountId").doesNotExist());
  }

  @Test
  @DisplayName("PUT /financeiro/settings persiste defaults")
  void putSettingsPersist() throws Exception {
    String chartRevenueId = insertChartAccount("4.01.001", "Receita Vendas", "REVENUE");

    mockMvc
        .perform(
            put("/api/v1/financeiro/settings")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "defaultRevenueChartAccountId":"%s",
                      "defaultExpenseChartAccountId":"%s",
                      "defaultCostCenterId":"%s"
                    }
                    """
                        .formatted(chartRevenueId, chartExpenseId, costCenterId)))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.defaultRevenueChartAccountId").value(chartRevenueId))
        .andExpect(jsonPath("$.defaultExpenseChartAccountId").value(chartExpenseId))
        .andExpect(jsonPath("$.defaultCostCenterId").value(costCenterId));
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private void configureSettings(String chartExpenseId, String costCenterId) throws Exception {
    mockMvc
        .perform(
            put("/api/v1/financeiro/settings")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "defaultExpenseChartAccountId":"%s",
                      "defaultCostCenterId":"%s"
                    }
                    """
                        .formatted(chartExpenseId, costCenterId)))
        .andExpect(status().isOk());
  }

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
    mockMvc
        .perform(
            post("/api/v1/compras/orders/" + order.get("id").asText() + "/send")
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
                          "type":"PJ",
                          "legalName":"Fornecedor Listener LTDA",
                          "cnpj":"%s",
                          "modalities":["SUPPLIER"]
                        }
                        """
                            .formatted(CNPJ_SUPPLIER)))
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
                    {"branchId":"%s","code":"DEP-01","name":"Depósito","asDefault":true}
                    """
                        .formatted(branchId)))
        .andExpect(status().isCreated());
  }

  private String insertChartAccount(String code, String name, String type) {
    UUID id = UuidCreator.getTimeOrderedEpoch();
    jdbc.execute("SET search_path TO " + tenantSchema + ", public");
    jdbc.update(
        "INSERT INTO chart_account (id, code, name, type, is_synthetic, is_active, sort_order, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, false, true, 0, now(), now())",
        id,
        code,
        name,
        type);
    return id.toString();
  }

  private String insertCostCenter(String code, String name) {
    UUID id = UuidCreator.getTimeOrderedEpoch();
    jdbc.execute("SET search_path TO " + tenantSchema + ", public");
    jdbc.update(
        "INSERT INTO cost_center (id, code, name, is_active, sort_order, created_at, updated_at) "
            + "VALUES (?, ?, ?, true, 0, now(), now())",
        id,
        code,
        name);
    return id.toString();
  }

  private LoginApiResponse doLogin(String identifier, String password) throws Exception {
    MvcResult res =
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
    return objectMapper.readValue(res.getResponse().getContentAsString(), LoginApiResponse.class);
  }
}
