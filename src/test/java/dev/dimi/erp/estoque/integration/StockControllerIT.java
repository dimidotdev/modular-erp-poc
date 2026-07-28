package dev.dimi.erp.estoque.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
 * IT do fluxo HTTP de estoque: location, snapshot, adjustment (step-up), reverse e ledger. WAC e
 * invariantes de saldo são validados isoladamente em {@code StockSnapshotTest}; aqui focamos em
 * persistência, idempotência via chave natural e step-up. TRANSFER fica para IT específico futuro
 * (exige duas branches no tenant).
 */
class StockControllerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;
  private String branchId;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "stock-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Stock IT Co",
          "legalName": "Stock IT Limitada",
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

    // Matriz é seedada por TenantProvisioner; GET /branches devolve um Page com content[].
    MvcResult branches =
        mockMvc
            .perform(
                get("/api/v1/cadastros/branches").header("Authorization", "Bearer " + accessToken))
            .andExpect(status().isOk())
            .andReturn();
    JsonNode page = objectMapper.readTree(branches.getResponse().getContentAsString());
    branchId = page.get("content").get(0).get("id").asText();
  }

  @Test
  @DisplayName("POST /locations cria local e GET busca pelo id + lista pela filial")
  void location_crud() throws Exception {
    String locationId = createLocation(branchId, "DEP-01", "Depósito 01");

    mockMvc
        .perform(
            get("/api/v1/estoque/locations/" + locationId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value("DEP-01"))
        .andExpect(jsonPath("$.name").value("Depósito 01"))
        .andExpect(jsonPath("$.isDefault").value(true));

    mockMvc
        .perform(
            get("/api/v1/estoque/locations")
                .param("branchId", branchId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].code").value("DEP-01"));
  }

  @Test
  @DisplayName("POST /adjustments sem step-up devolve 401 com WWW-Authenticate StepUp")
  void adjustSemStepUp_401() throws Exception {
    String locationId = createLocation(branchId, "DEP-01", "Depósito 01");
    String packagingId = createPackaging("ADJ-A");

    mockMvc
        .perform(
            post("/api/v1/estoque/adjustments")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "packagingId":"%s",
                      "branchId":"%s",
                      "locationId":"%s",
                      "delta":3,
                      "reason":"contagem"
                    }
                    """
                        .formatted(packagingId, branchId, locationId)))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("StepUp")));
  }

  @Test
  @DisplayName("POST /adjustments com step-up grava ADJUSTMENT e snapshot reflete o saldo")
  void adjustComStepUp_atualizaSaldo() throws Exception {
    String locationId = createLocation(branchId, "DEP-01", "Depósito 01");
    String packagingId = createPackaging("ADJ-OK");
    String steppedToken = doStepUp();

    String movementId =
        adjust(steppedToken, packagingId, branchId, locationId, "7", "contagem inicial");

    mockMvc
        .perform(
            get("/api/v1/estoque/movements/" + movementId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.direction").value("ADJUSTMENT"))
        .andExpect(jsonPath("$.quantity").value(7.0))
        .andExpect(jsonPath("$.reason").value("contagem inicial"));

    mockMvc
        .perform(
            get("/api/v1/estoque/snapshots")
                .param("packagingId", packagingId)
                .param("branchId", branchId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].quantityOnHand").value(7.0))
        .andExpect(jsonPath("$[0].quantityReserved").value(0.0));
  }

  @Test
  @DisplayName(
      "POST /movements/{id}/reverse de ADJUSTMENT devolve 400 — correção é via novo ADJUSTMENT inverso")
  void reverseDeAdjustment_400() throws Exception {
    String locationId = createLocation(branchId, "DEP-01", "Depósito 01");
    String packagingId = createPackaging("REV-A");
    String steppedToken = doStepUp();

    String adjId = adjust(steppedToken, packagingId, branchId, locationId, "5", "subida inicial");

    String steppedToken2 = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/estoque/movements/" + adjId + "/reverse")
                .header("Authorization", "Bearer " + steppedToken2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"estorno de inventário\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("estoque.invalid-operation"))
        .andExpect(jsonPath("$.reason").value("reverse-of-adjustment"));
  }

  @Test
  @DisplayName("ADJUSTMENT com delta negativo subtrai saldo; correção via novo ADJUSTMENT positivo")
  void adjustNegativoEContraAjuste() throws Exception {
    String locationId = createLocation(branchId, "DEP-01", "Depósito 01");
    String packagingId = createPackaging("ADJ-NEG");
    String steppedToken1 = doStepUp();

    adjust(steppedToken1, packagingId, branchId, locationId, "10", "subida inicial");

    String steppedToken2 = doStepUp();
    adjust(steppedToken2, packagingId, branchId, locationId, "-3", "ajuste para baixo");

    mockMvc
        .perform(
            get("/api/v1/estoque/snapshots")
                .param("packagingId", packagingId)
                .param("branchId", branchId)
                .header("Authorization", "Bearer " + accessToken))
        .andExpect(jsonPath("$[0].quantityOnHand").value(7.0));
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private String createLocation(String branchId, String code, String name) throws Exception {
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/estoque/locations")
                    .header("Authorization", "Bearer " + accessToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"branchId":"%s","code":"%s","name":"%s","asDefault":true}
                        """
                            .formatted(branchId, code, name)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }

  /** Cria produto + variant + packaging e devolve o packagingId. */
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

  private String adjust(
      String steppedToken,
      String packagingId,
      String branchId,
      String locationId,
      String delta,
      String reason)
      throws Exception {
    MvcResult res =
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
                          "delta":%s,
                          "reason":"%s"
                        }
                        """
                            .formatted(packagingId, branchId, locationId, delta, reason)))
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
