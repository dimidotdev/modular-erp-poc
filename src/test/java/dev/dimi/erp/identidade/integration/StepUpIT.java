package dev.dimi.erp.identidade.integration;

import static org.assertj.core.api.Assertions.assertThat;
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
 * IT do endpoint de step-up ({@code POST /api/v1/auth/step-up}): valida senha, devolve access token
 * novo com claim {@code sua} dentro da janela configurada (PT5M em test).
 */
class StepUpIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  private String tenantSlug;
  private String temporaryPassword;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "step-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "StepUp IT Co",
          "legalName": "StepUp IT Limitada",
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
  }

  @Test
  @DisplayName("POST /auth/step-up com senha correta devolve access token novo")
  void stepUp_caminhoFeliz() throws Exception {
    LoginApiResponse login = doLogin("owner", temporaryPassword);

    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/auth/step-up")
                    .header("Authorization", "Bearer " + login.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"password\":\"" + temporaryPassword + "\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.accessToken").isString())
            .andExpect(jsonPath("$.accessTokenExpiresInSeconds").isNumber())
            .andExpect(jsonPath("$.stepUpAt").exists())
            .andReturn();

    String steppedToken =
        objectMapper.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    assertThat(steppedToken).isNotEqualTo(login.accessToken());
  }

  @Test
  @DisplayName("POST /auth/step-up com senha errada devolve 401 e não emite token")
  void stepUp_senhaErrada_401() throws Exception {
    LoginApiResponse login = doLogin("owner", temporaryPassword);

    mockMvc
        .perform(
            post("/api/v1/auth/step-up")
                .header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"senhaInvalida-AAA-1!\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("POST /auth/step-up sem JWT devolve 401 (não autenticado)")
  void stepUp_semJwt_401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/step-up")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"qualquer\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("token sem step-up rejeitado em endpoint sensível com 401 + WWW-Authenticate")
  void semStepUp_endpointSensivel_401StepUpRequired() throws Exception {
    LoginApiResponse login = doLogin("owner", temporaryPassword);

    String personId = createPF(login.accessToken());

    mockMvc
        .perform(
            post("/api/v1/cadastros/persons/" + personId + "/anonimizar")
                .header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"reason\":\"pedido do titular\"}"))
        .andExpect(status().isUnauthorized())
        .andExpect(header().exists("WWW-Authenticate"))
        .andExpect(
            header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("StepUp")));
  }

  // --------------------------------------------------------------------------
  // Helpers
  // --------------------------------------------------------------------------

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

  private String createPF(String accessToken) throws Exception {
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
                          "legalName": "Cliente Step-Up",
                          "cpf": "11144477735",
                          "modalities": ["CUSTOMER"]
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();
  }
}
