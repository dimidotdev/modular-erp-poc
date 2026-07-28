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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do slice 7.2: lifecycle de MVF (financial_record) — open / cancel / renegotiate.
 *
 * <p>Catálogos {@code chart_account} e {@code cost_center} ainda não têm controller (entram em
 * slice futuro); inseridos via JdbcTemplate no setup com {@code SET search_path} para o schema do
 * tenant criado.
 */
class FinancialRecordFlowIT extends AbstractWebIntegrationTest {

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

  @BeforeEach
  void setUp() throws Exception {
    tenantSlug = "fin-" + System.nanoTime();
    tenantSchema = "tenant_" + tenantSlug.replace('-', '_');

    String provBody =
        """
        {
          "slug": "%s",
          "displayName": "Fin IT Co",
          "legalName": "Fin IT Limitada",
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
  }

  @Test
  @DisplayName("POST sem rateio → 400")
  void semRateio_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/financeiro/records")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "branchId":"%s",
                      "direction":"DEBIT",
                      "counterpartyId":"%s",
                      "totalValue":100.00,
                      "originType":"MANUAL",
                      "installments":[
                        {"dueDate":"2026-06-15","originalValue":100.00}
                      ],
                      "apportionments":[]
                    }
                    """
                        .formatted(branchId, counterpartyId)))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("POST com soma de parcelas ≠ total → 400")
  void parcelasNaoBatemTotal_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/financeiro/records")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(openBody("100.00", "60.00", "100.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("financeiro.invalid-operation"));
  }

  @Test
  @DisplayName("POST com soma de rateios ≠ total → 400")
  void rateiosNaoBatemTotal_400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/financeiro/records")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(openBody("100.00", "100.00", "70.00")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("financeiro.invalid-operation"));
  }

  @Test
  @DisplayName("MVF a pagar manual abre OPEN com 2 parcelas + 1 rateio")
  void abreManualOK() throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/financeiro/records")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(openBodyTwoInstallments()))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.direction").value("DEBIT"))
            .andExpect(jsonPath("$.totalValue").value(200.00))
            .andExpect(jsonPath("$.installments.length()").value(2))
            .andExpect(jsonPath("$.apportionments.length()").value(1))
            .andReturn();
    JsonNode mvf = objectMapper.readTree(res.getResponse().getContentAsString());
    assertThat(mvf.get("number").asLong()).isPositive();
    assertThat(mvf.get("installments").get(0).get("status").asText()).isEqualTo("OPEN");
  }

  @Test
  @DisplayName("mesma idempotencyKey → mesma MVF (sem duplicar)")
  void idempotenciaPorChave() throws Exception {
    String idemKey = "mvf:dup:" + System.nanoTime();
    String body = openBodyWithKey(idemKey);

    MvcResult first =
        mockMvc
            .perform(
                post("/api/v1/financeiro/records")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String firstId =
        objectMapper.readTree(first.getResponse().getContentAsString()).get("id").asText();

    MvcResult second =
        mockMvc
            .perform(
                post("/api/v1/financeiro/records")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    String secondId =
        objectMapper.readTree(second.getResponse().getContentAsString()).get("id").asText();

    assertThat(secondId).isEqualTo(firstId);
  }

  @Test
  @DisplayName("cancel com step-up → CANCELLED + parcelas viram CANCELLED")
  void cancelOK() throws Exception {
    String mvfId = createMvf();
    String steppedToken = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/financeiro/records/" + mvfId + "/cancel")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"erro de lançamento\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.status").value("CANCELLED"))
        .andExpect(jsonPath("$.installments[0].status").value("CANCELLED"));
  }

  @Test
  @DisplayName("renegotiate gera nova MVF OPEN e marca original RENEGOTIATED")
  void renegotiateOK() throws Exception {
    String originalId = createMvf();
    String steppedToken = doStepUp();

    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/financeiro/records/" + originalId + "/renegotiate")
                    .header("Authorization", "Bearer " + steppedToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "newTotalValue":210.00,
                          "reason":"acordo com fornecedor",
                          "newInstallments":[
                            {"dueDate":"2026-07-15","originalValue":105.00},
                            {"dueDate":"2026-08-15","originalValue":105.00}
                          ]
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("OPEN"))
            .andExpect(jsonPath("$.totalValue").value(210.00))
            .andReturn();
    JsonNode replacement = objectMapper.readTree(res.getResponse().getContentAsString());
    assertThat(replacement.get("id").asText()).isNotEqualTo(originalId);

    mockMvc
        .perform(
            get("/api/v1/financeiro/records/" + originalId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$.status").value("RENEGOTIATED"))
        .andExpect(jsonPath("$.installments[0].status").value("RENEGOTIATED"));
  }

  // --------------------------------------------------------------------------
  // Helpers
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

  private String openBody(String totalValue, String installmentValue, String apportionmentValue) {
    return """
        {
          "branchId":"%s",
          "direction":"DEBIT",
          "counterpartyId":"%s",
          "totalValue":%s,
          "originType":"MANUAL",
          "installments":[
            {"dueDate":"2026-06-15","originalValue":%s}
          ],
          "apportionments":[
            {"chartAccountId":"%s","costCenterId":"%s","value":%s}
          ]
        }
        """
        .formatted(
            branchId,
            counterpartyId,
            totalValue,
            installmentValue,
            chartAccountId,
            costCenterId,
            apportionmentValue);
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

  private String openBodyWithKey(String idemKey) {
    return """
        {
          "branchId":"%s",
          "direction":"DEBIT",
          "counterpartyId":"%s",
          "totalValue":200.00,
          "originType":"MANUAL",
          "idempotencyKey":"%s",
          "installments":[
            {"dueDate":"2026-06-15","originalValue":100.00},
            {"dueDate":"2026-07-15","originalValue":100.00}
          ],
          "apportionments":[
            {"chartAccountId":"%s","costCenterId":"%s","value":200.00}
          ]
        }
        """
        .formatted(branchId, counterpartyId, idemKey, chartAccountId, costCenterId);
  }

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
