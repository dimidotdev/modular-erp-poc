package dev.dimi.erp.financeiro.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do slice 7.3: lifecycle de pagamento financeiro — batch de aplicações + estorno.
 *
 * <p>Provisiona tenant, cria branch via login do owner, insere counterparty / chart_account /
 * cost_center / bank_account via JdbcTemplate (catálogos sem controller no slice 7.x), abre uma MVF
 * com 2 parcelas de R$100 e exercita os fluxos de POST /payments e POST /payments/{id}/reverse.
 */
class FinancialPaymentFlowIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CNPJ_COUNTERPARTY = "11222333000181";

  @Autowired JdbcTemplate jdbc;

  private String tenantSlug;
  private String tenantSchema;
  private String temporaryPassword;
  private String accessToken;
  private String branchId;
  private String counterpartyId;
  private String chartAccountId;
  private String costCenterId;
  private String bankAccountId;

  @BeforeEach
  void setUp() throws Exception {
    tenantSlug = "pay-" + System.nanoTime();
    tenantSchema = "tenant_" + tenantSlug.replace('-', '_');

    String provBody =
        """
        {
          "slug": "%s",
          "displayName": "Pay IT Co",
          "legalName": "Pay IT Limitada",
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
                    .content(provBody))
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

    counterpartyId = createCounterparty();
    chartAccountId = insertChartAccount("3.01.001", "Fornecedores", "EXPENSE");
    costCenterId = insertCostCenter("CC.001", "Centro Geral");
    bankAccountId = insertBankAccount("CX-01", "Caixa Geral", "CASH", chartAccountId);
  }

  // ---------------------------------------------------------------------------
  // POST /payments
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("POST /payments sem Idempotency-Key → 400 financeiro.invalid-operation")
  void semIdempotencyKey_400() throws Exception {
    String mvfId = createMvf();
    String firstInstallmentId = firstInstallmentId(mvfId);

    mockMvc
        .perform(
            post("/api/v1/financeiro/payments")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchBody(firstInstallmentId, "30.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("financeiro.invalid-operation"));
  }

  @Test
  @DisplayName("Pagamento parcial → parcela PARTIALLY_PAID + MVF PARTIALLY_PAID")
  void pagamentoParcial_INSTALLMENT_PARTIALLY_PAID() throws Exception {
    String mvfId = createMvf();
    String firstInstallmentId = firstInstallmentId(mvfId);

    mockMvc
        .perform(
            post("/api/v1/financeiro/payments")
                .header("Authorization", "Bearer " + accessToken)
                .header("Idempotency-Key", "pay:partial:" + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchBody(firstInstallmentId, "30.00")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/financeiro/records/" + mvfId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.installments[0].status").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.installments[1].status").value("OPEN"));
  }

  @Test
  @DisplayName("Pagamento total de uma parcela → parcela PAID, MVF PARTIALLY_PAID (outra OPEN)")
  void pagamentoTotalDeUmaParcela_INSTALLMENT_PAID() throws Exception {
    String mvfId = createMvf();
    String firstInstallmentId = firstInstallmentId(mvfId);

    mockMvc
        .perform(
            post("/api/v1/financeiro/payments")
                .header("Authorization", "Bearer " + accessToken)
                .header("Idempotency-Key", "pay:full1:" + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchBody(firstInstallmentId, "100.00")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/financeiro/records/" + mvfId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.installments[0].status").value("PAID"))
        .andExpect(jsonPath("$.installments[1].status").value("OPEN"));
  }

  @Test
  @DisplayName("Paga todas as parcelas → ambas PAID + MVF PAID")
  void pagaTodasParcelas_MVF_PAID() throws Exception {
    String mvfId = createMvf();
    String[] ids = bothInstallmentIds(mvfId);

    mockMvc
        .perform(
            post("/api/v1/financeiro/payments")
                .header("Authorization", "Bearer " + accessToken)
                .header("Idempotency-Key", "pay:all:" + System.nanoTime())
                .contentType(MediaType.APPLICATION_JSON)
                .content(batchBodyTwoApplications(ids[0], "100.00", ids[1], "100.00")))
        .andExpect(status().isCreated());

    mockMvc
        .perform(
            get("/api/v1/financeiro/records/" + mvfId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("PAID"))
        .andExpect(jsonPath("$.installments[0].status").value("PAID"))
        .andExpect(jsonPath("$.installments[1].status").value("PAID"));
  }

  @Test
  @DisplayName("Mesma Idempotency-Key → mesmo PaymentId (sem reaplicar)")
  void idempotencia() throws Exception {
    String mvfId = createMvf();
    String firstInstallmentId = firstInstallmentId(mvfId);
    String idemKey = "pay:idem:" + System.nanoTime();
    String body = batchBody(firstInstallmentId, "30.00");

    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/financeiro/payments")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String firstId =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

    MvcResult second =
        mockMvc
            .perform(
                post("/api/v1/financeiro/payments")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", idemKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String secondId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

    assertThat(secondId).isEqualTo(firstId);

    // E só houve 1 pagamento de R$30 (não 2): parcela continua PARTIALLY_PAID com paidValue=30.
    mockMvc
        .perform(
            get("/api/v1/financeiro/records/" + mvfId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.installments[0].status").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.installments[0].paidValue").value(30.00));
  }

  // ---------------------------------------------------------------------------
  // POST /payments/{id}/reverse
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Estorno com step-up → parcela volta a OPEN + MVF OPEN")
  void estornoComStepUp_OK() throws Exception {
    String mvfId = createMvf();
    String firstInstallmentId = firstInstallmentId(mvfId);

    MvcResult paymentRes =
        mockMvc
            .perform(
                post("/api/v1/financeiro/payments")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", "pay:rev:" + System.nanoTime())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchBody(firstInstallmentId, "100.00")))
            .andExpect(status().isCreated())
            .andReturn();
    String paymentId =
        objectMapper.readTree(paymentRes.getResponse().getContentAsString()).get("id").asText();

    // Pre-condição: parcela PAID, MVF PARTIALLY_PAID.
    mockMvc
        .perform(
            get("/api/v1/financeiro/records/" + mvfId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("PARTIALLY_PAID"))
        .andExpect(jsonPath("$.installments[0].status").value("PAID"));

    // Snapshot do batch_id do pagamento original para conferência DB-side.
    jdbc.execute("SET search_path TO " + tenantSchema + ", public");
    UUID batchId =
        jdbc.queryForObject(
            "SELECT batch_id FROM financial_payment WHERE id = ?::uuid", UUID.class, paymentId);
    assertThat(batchId).as("batch_id do pagamento original").isNotNull();

    String steppedToken = doStepUp();

    MvcResult reverseRes =
        mockMvc
            .perform(
                post("/api/v1/financeiro/payments/" + paymentId + "/reverse")
                    .header("Authorization", "Bearer " + steppedToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"pagamento duplicado\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String reversalPaymentId =
        objectMapper.readTree(reverseRes.getResponse().getContentAsString()).get("id").asText();
    assertThat(reversalPaymentId)
        .as("id do pagamento espelho deve ser diferente do original")
        .isNotEqualTo(paymentId);

    // (a) HTTP: MVF e parcela voltaram a OPEN.
    mockMvc
        .perform(
            get("/api/v1/financeiro/records/" + mvfId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("OPEN"))
        .andExpect(jsonPath("$.installments[0].status").value("OPEN"));

    // (b) DB: existem 2 rows em financial_payment para o mesmo batch — original + reversal.
    jdbc.execute("SET search_path TO " + tenantSchema + ", public");
    Integer paymentRows =
        jdbc.queryForObject(
            "SELECT count(*)::int FROM financial_payment WHERE batch_id = ?",
            Integer.class,
            batchId);
    assertThat(paymentRows).as("financial_payment rows para o batch").isEqualTo(2);

    // (c) DB: o pagamento espelho tem is_reversal=true; o original mantém is_reversal=false.
    Boolean reversalFlag =
        jdbc.queryForObject(
            "SELECT is_reversal FROM financial_payment WHERE id = ?::uuid",
            Boolean.class,
            reversalPaymentId);
    assertThat(reversalFlag).as("financial_payment.is_reversal do espelho").isTrue();
    Boolean originalFlag =
        jdbc.queryForObject(
            "SELECT is_reversal FROM financial_payment WHERE id = ?::uuid",
            Boolean.class,
            paymentId);
    assertThat(originalFlag).as("financial_payment.is_reversal do original").isFalse();

    // (d) DB: financial_reversal materializa o par (original, reversal).
    Integer reversalRows =
        jdbc.queryForObject(
            "SELECT count(*)::int FROM financial_reversal "
                + "WHERE original_payment_id = ?::uuid AND reversal_payment_id = ?::uuid",
            Integer.class,
            paymentId,
            reversalPaymentId);
    assertThat(reversalRows).as("financial_reversal pareando original e espelho").isEqualTo(1);

    // (e) DB: a aplicação espelho tem value negativo e is_reversal=true. Como o pagamento
    // foi de R$100 puro (sem multa/juros/desconto), conferimos value=-100 e os demais 0.
    java.util.List<java.util.Map<String, Object>> mirrorApps =
        jdbc.queryForList(
            "SELECT value, fine_applied, interest_applied, discount_applied, is_reversal "
                + "FROM financial_payment_application WHERE payment_id = ?::uuid",
            reversalPaymentId);
    assertThat(mirrorApps).as("aplicações do pagamento espelho").hasSize(1);
    java.util.Map<String, Object> mirrorApp = mirrorApps.get(0);
    assertThat(((java.math.BigDecimal) mirrorApp.get("value")))
        .as("value da aplicação espelho deve ser -100.00")
        .isEqualByComparingTo(new java.math.BigDecimal("-100.00"));
    assertThat(((java.math.BigDecimal) mirrorApp.get("fine_applied"))).isEqualByComparingTo("0");
    assertThat(((java.math.BigDecimal) mirrorApp.get("interest_applied")))
        .isEqualByComparingTo("0");
    assertThat(((java.math.BigDecimal) mirrorApp.get("discount_applied")))
        .isEqualByComparingTo("0");
    assertThat((Boolean) mirrorApp.get("is_reversal"))
        .as("financial_payment_application.is_reversal do espelho")
        .isTrue();

    // (f) DB: a aplicação original continua com value positivo e is_reversal=false.
    java.util.List<java.util.Map<String, Object>> originalApps =
        jdbc.queryForList(
            "SELECT value, is_reversal "
                + "FROM financial_payment_application WHERE payment_id = ?::uuid",
            paymentId);
    assertThat(originalApps).as("aplicações do pagamento original").hasSize(1);
    assertThat(((java.math.BigDecimal) originalApps.get(0).get("value")))
        .isEqualByComparingTo(new java.math.BigDecimal("100.00"));
    assertThat((Boolean) originalApps.get(0).get("is_reversal")).isFalse();

    // (g) DB: a parcela teve paid_value revertido para 0 e status OPEN.
    java.util.Map<String, Object> installmentRow =
        jdbc.queryForMap(
            "SELECT paid_value, status FROM financial_installment WHERE id = ?::uuid",
            firstInstallmentId);
    assertThat(((java.math.BigDecimal) installmentRow.get("paid_value")))
        .as("financial_installment.paid_value após estorno")
        .isEqualByComparingTo("0");
    assertThat(installmentRow.get("status"))
        .as("financial_installment.status após estorno")
        .isEqualTo("OPEN");
  }

  @Test
  @DisplayName("Estorno sem reason → 400")
  void estornoSemReason_400() throws Exception {
    String mvfId = createMvf();
    String firstInstallmentId = firstInstallmentId(mvfId);

    MvcResult paymentRes =
        mockMvc
            .perform(
                post("/api/v1/financeiro/payments")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", "pay:nor:" + System.nanoTime())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchBody(firstInstallmentId, "100.00")))
            .andExpect(status().isCreated())
            .andReturn();
    String paymentId =
        objectMapper.readTree(paymentRes.getResponse().getContentAsString()).get("id").asText();

    String steppedToken = doStepUp();

    mockMvc
        .perform(
            post("/api/v1/financeiro/payments/" + paymentId + "/reverse")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("Duplo estorno → retorna o mesmo pagamento espelho (UNIQUE original_payment_id)")
  void duploEstorno_retornaMesmoEstorno() throws Exception {
    String mvfId = createMvf();
    String firstInstallmentId = firstInstallmentId(mvfId);

    MvcResult paymentRes =
        mockMvc
            .perform(
                post("/api/v1/financeiro/payments")
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Idempotency-Key", "pay:dup:" + System.nanoTime())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(batchBody(firstInstallmentId, "100.00")))
            .andExpect(status().isCreated())
            .andReturn();
    String paymentId =
        objectMapper.readTree(paymentRes.getResponse().getContentAsString()).get("id").asText();

    String steppedToken = doStepUp();

    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/financeiro/payments/" + paymentId + "/reverse")
                    .header("Authorization", "Bearer " + steppedToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"primeiro estorno\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String firstMirrorId =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

    MvcResult second =
        mockMvc
            .perform(
                post("/api/v1/financeiro/payments/" + paymentId + "/reverse")
                    .header("Authorization", "Bearer " + steppedToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"reason\":\"tentando estornar de novo\"}"))
            .andExpect(status().isOk())
            .andReturn();
    String secondMirrorId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

    assertThat(secondMirrorId).isEqualTo(firstMirrorId);
  }

  // ---------------------------------------------------------------------------
  // Concorrência — optimistic-lock retry
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("Dois batches simultâneos na mesma parcela → retry resolve, sem lost update")
  void concorrencia_doisBatchesNaMesmaParcela_naoPerdeUpdate() throws Exception {
    String mvfId = createMvfSingleInstallment();
    String installmentId = firstInstallmentId(mvfId);

    String idem1 = "race1:" + System.nanoTime();
    String idem2 = "race2:" + System.nanoTime();

    CompletableFuture<Integer> f1 =
        CompletableFuture.supplyAsync(() -> performPayment(idem1, installmentId, "100.00"));
    CompletableFuture<Integer> f2 =
        CompletableFuture.supplyAsync(() -> performPayment(idem2, installmentId, "100.00"));

    int status1 = f1.get(15, TimeUnit.SECONDS);
    int status2 = f2.get(15, TimeUnit.SECONDS);

    // (a) HTTP: ambos retornaram 201 — retry tratou a colisão de versão transparentemente.
    assertThat(status1).as("status do batch 1").isEqualTo(201);
    assertThat(status2).as("status do batch 2").isEqualTo(201);

    // (b) DB: paid_value = 200 (sem lost update) e status PAID.
    jdbc.execute("SET search_path TO " + tenantSchema + ", public");
    java.util.Map<String, Object> installmentRow =
        jdbc.queryForMap(
            "SELECT paid_value, status FROM financial_installment WHERE id = ?::uuid",
            installmentId);
    assertThat(((java.math.BigDecimal) installmentRow.get("paid_value")))
        .as("financial_installment.paid_value após 2 batches concorrentes")
        .isEqualByComparingTo("200.00");
    assertThat(installmentRow.get("status"))
        .as("financial_installment.status após pagamento total")
        .isEqualTo("PAID");

    // (c) DB: exatamente 2 aplicações de pagamento materializadas para a parcela.
    Integer applicationRows =
        jdbc.queryForObject(
            "SELECT count(*)::int FROM financial_payment_application WHERE installment_id = ?::uuid",
            Integer.class,
            installmentId);
    assertThat(applicationRows)
        .as("financial_payment_application rows para a parcela")
        .isEqualTo(2);
  }

  /**
   * Wrapper síncrono em torno de {@link #mockMvc} para uso dentro de {@link CompletableFuture}.
   * Retorna o HTTP status para que a asserção viva no thread do teste.
   */
  private int performPayment(String idemKey, String installmentId, String value) {
    try {
      return mockMvc
          .perform(
              post("/api/v1/financeiro/payments")
                  .header("Authorization", "Bearer " + accessToken)
                  .header("Idempotency-Key", idemKey)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(batchBody(installmentId, value)))
          .andReturn()
          .getResponse()
          .getStatus();
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  // --------------------------------------------------------------------------
  // Helpers — MVF + pagamento
  // --------------------------------------------------------------------------

  private String createMvf() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/financeiro/records")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(openBodyTwoInstallments()))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private String createMvfSingleInstallment() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/financeiro/records")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(openBodySingleInstallment()))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  private String firstInstallmentId(String mvfId) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/financeiro/records/" + mvfId)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
    return body.get("installments").get(0).get("id").asText();
  }

  private String[] bothInstallmentIds(String mvfId) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                get("/api/v1/financeiro/records/" + mvfId)
                    .header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode body = objectMapper.readTree(res.getResponse().getContentAsString());
    return new String[] {
      body.get("installments").get(0).get("id").asText(),
      body.get("installments").get(1).get("id").asText()
    };
  }

  private String openBodyTwoInstallments() {
    return """
        {
          "branchId":"%s",
          "direction":"DEBIT",
          "counterpartyId":"%s",
          "totalValue":200.00,
          "originType":"MANUAL",
          "notes":"compra de material",
          "installments":[
            {"dueDate":"2026-06-15","originalValue":100.00},
            {"dueDate":"2026-07-15","originalValue":100.00}
          ],
          "apportionments":[
            {"chartAccountId":"%s","costCenterId":"%s","value":200.00}
          ]
        }
        """
        .formatted(branchId, counterpartyId, chartAccountId, costCenterId);
  }

  private String openBodySingleInstallment() {
    return """
        {
          "branchId":"%s",
          "direction":"DEBIT",
          "counterpartyId":"%s",
          "totalValue":200.00,
          "originType":"MANUAL",
          "notes":"corrida concorrente",
          "installments":[
            {"dueDate":"2026-06-15","originalValue":200.00}
          ],
          "apportionments":[
            {"chartAccountId":"%s","costCenterId":"%s","value":200.00}
          ]
        }
        """
        .formatted(branchId, counterpartyId, chartAccountId, costCenterId);
  }

  private String batchBody(String installmentId, String value) {
    return """
        {
          "bankAccountId":"%s",
          "method":"CASH",
          "applications":[
            {"installmentId":"%s","value":%s}
          ]
        }
        """
        .formatted(bankAccountId, installmentId, value);
  }

  private String batchBodyTwoApplications(
      String installmentId1, String value1, String installmentId2, String value2) {
    return """
        {
          "bankAccountId":"%s",
          "method":"CASH",
          "applications":[
            {"installmentId":"%s","value":%s},
            {"installmentId":"%s","value":%s}
          ]
        }
        """
        .formatted(bankAccountId, installmentId1, value1, installmentId2, value2);
  }

  // --------------------------------------------------------------------------
  // Helpers — setup / catálogos
  // --------------------------------------------------------------------------

  private String createCounterparty() throws Exception {
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
                          "legalName":"Fornecedor Teste LTDA",
                          "cnpj":"%s",
                          "modalities":["SUPPLIER"]
                        }
                        """
                            .formatted(CNPJ_COUNTERPARTY)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
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

  private String insertBankAccount(String code, String name, String type, String chartAccountId) {
    UUID id = UuidCreator.getTimeOrderedEpoch();
    jdbc.execute("SET search_path TO " + tenantSchema + ", public");
    jdbc.update(
        "INSERT INTO bank_account (id, code, name, type, chart_account_id, initial_balance, is_active, created_at, updated_at) "
            + "VALUES (?, ?, ?, ?, ?::uuid, 0, true, now(), now())",
        id,
        code,
        name,
        type,
        chartAccountId);
    return id.toString();
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
