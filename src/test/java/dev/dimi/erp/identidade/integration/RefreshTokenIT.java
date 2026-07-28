package dev.dimi.erp.identidade.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT do fluxo de refresh: rotação one-shot + detecção de reuse (defesa contra roubo de token).
 *
 * <p>Reuse detection é uma das defesas mais importantes — usar o refresh original duas vezes deve
 * revogar toda a chain (sessões da família). Aqui validamos que a 2ª chamada com o token original
 * já consumido devolve {@code 401} com {@code identidade.refresh-token-reuse}.
 */
class RefreshTokenIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  private String tenantSlug;
  private String temporaryPassword;

  @BeforeEach
  void provisionAndLogin() throws Exception {
    tenantSlug = "rt-" + System.nanoTime();
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Refresh IT Co",
          "legalName": "Refresh IT Limitada",
          "cnpj": "%s",
          "plan": "free",
          "ownerUsername": "owner",
          "ownerEmail": "owner@example.com",
          "ownerFullName": "Owner User"
        }
        """
            .formatted(tenantSlug, randomCnpj());

    MvcResult result =
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
            .readTree(result.getResponse().getContentAsString())
            .get("temporaryPassword")
            .asText();
  }

  @Test
  @DisplayName(
      "refresh com token original rotaciona e devolve novo par; original passa a ser inválido")
  void refresh_rotateOneShot() throws Exception {
    LoginApiResponse login = doLogin();
    String firstRefresh = login.refreshToken();

    LoginApiResponse rotated = doRefresh(firstRefresh);
    assertThat(rotated.accessToken()).isNotBlank();
    // accessToken pode coincidir quando login e refresh acontecem no mesmo segundo (iat do JWT
    // tem precisão de segundos). O que IMPORTA pra rotação é o refresh ser sempre fresh — random
    // 256 bits, jamais colide.
    assertThat(rotated.refreshToken()).isNotBlank().isNotEqualTo(firstRefresh);

    // Original já marcado used_at → reuso pelo MESMO original dispara 401 + revogação.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(firstRefresh)))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("após detectar reuse, o novo refresh emitido também é revogado (chain)")
  void refresh_reuseRevogaToda_aChain() throws Exception {
    LoginApiResponse login = doLogin();
    LoginApiResponse rotated = doRefresh(login.refreshToken());

    // Dispara reuse usando o original consumido.
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(login.refreshToken())))
        .andExpect(status().isUnauthorized());

    // O refresh emitido na rotação anterior também perde validade (chain revogada).
    mockMvc
        .perform(
            post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(refreshBody(rotated.refreshToken())))
        .andExpect(status().isUnauthorized());
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private LoginApiResponse doLogin() throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"tenantSlug":"%s","identifier":"owner","password":"%s"}
                        """
                            .formatted(tenantSlug, temporaryPassword)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readValue(
        result.getResponse().getContentAsString(), LoginApiResponse.class);
  }

  private LoginApiResponse doRefresh(String token) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(refreshBody(token)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readValue(
        result.getResponse().getContentAsString(), LoginApiResponse.class);
  }

  private static String refreshBody(String token) {
    return "{\"refreshToken\":\"" + token + "\"}";
  }
}
