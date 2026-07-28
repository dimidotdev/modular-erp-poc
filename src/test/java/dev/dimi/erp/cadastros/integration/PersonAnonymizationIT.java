package dev.dimi.erp.cadastros.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT da anonimização LGPD (RN-CAD-011/012): {@code POST /persons/{id}/anonimizar} exige step-up,
 * zera PII, apaga endereços/contatos e bloqueia mutações subsequentes.
 */
class PersonAnonymizationIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";
  private static final String CPF_VALIDO = "11144477735";

  private String tenantSlug;
  private String temporaryPassword;
  private String accessToken;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "anon-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Anon IT Co",
          "legalName": "Anon IT Limitada",
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
  }

  @Test
  @DisplayName("anonimizar sem step-up devolve 401 com WWW-Authenticate StepUp")
  void semStepUp_401() throws Exception {
    String id = createPF();

    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + id + "/anonimizar")
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"pedido do titular\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(
            header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("StepUp")));
  }

  @Test
  @DisplayName("anonimizar com step-up zera PII e marca anonymized=true")
  void comStepUp_anonimiza() throws Exception {
    String id = createPF();
    String steppedToken = doStepUp();

    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + id + "/anonimizar")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"pedido do titular\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.anonymized").value(true))
        .andExpect(jsonPath("$.active").value(false))
        .andExpect(jsonPath("$.legalName").value("PESSOA ANONIMIZADA"))
        .andExpect(jsonPath("$.cpf").doesNotExist());

    mockMvc
        .perform(
            get("/api/v1/cadastros/persons/" + id).header("Authorization", "Bearer " + accessToken))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.anonymized").value(true))
        .andExpect(jsonPath("$.cpf").doesNotExist());
  }

  @Test
  @DisplayName("pessoa anonimizada rejeita PATCH (anonymized-immutable)")
  void anonimizada_naoAtualiza() throws Exception {
    String id = createPF();
    String steppedToken = doStepUp();
    anonimizar(id, steppedToken);

    mockMvc
        .perform(
            patch("/api/v1/cadastros/persons/" + id)
                .header("Authorization", "Bearer " + accessToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"legalName\":\"Tentando voltar\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  @DisplayName("anonimizar duas vezes a mesma pessoa devolve 409 already-anonymized")
  void anonimizada_naoReAnonimiza() throws Exception {
    String id = createPF();
    String steppedToken = doStepUp();
    anonimizar(id, steppedToken);

    String stepped2 = doStepUp();
    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + id + "/anonimizar")
                .header("Authorization", "Bearer " + stepped2)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"de novo\"}"))
        .andExpect(status().isConflict());
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

  private void anonimizar(String personId, String steppedToken) throws Exception {
    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + personId + "/anonimizar")
                .header("Authorization", "Bearer " + steppedToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"pedido do titular\"}"))
        .andExpect(status().isOk());
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

  private String createPF() throws Exception {
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
                          "legalName": "João Para Apagar",
                          "cpf": "%s",
                          "modalities": ["CUSTOMER"]
                        }
                        """
                            .formatted(CPF_VALIDO)))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
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
