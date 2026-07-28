package dev.dimi.erp.identidade.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.identidade.internal.web.dto.ProvisionTenantApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT end-to-end do fluxo de autenticação: provisiona tenant via {@code POST /platform/tenants}, faz
 * login, exercita {@code /auth/me}, força senha errada, força lockout progressivo.
 *
 * <p>Cada teste usa um slug único — evita colisão de estado entre testes mesmo se o contexto for
 * reusado.
 */
class AuthenticationIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  private String tenantSlug;
  private String ownerUsername;
  private String temporaryPassword;

  @BeforeEach
  void provisionTenant() throws Exception {
    tenantSlug = "auth-" + System.nanoTime();
    ownerUsername = "owner";
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Auth IT Co",
          "legalName": "Auth IT Limitada",
          "cnpj": "%s",
          "plan": "free",
          "ownerUsername": "%s",
          "ownerEmail": "owner@example.com",
          "ownerFullName": "Owner User"
        }
        """
            .formatted(tenantSlug, randomCnpj(), ownerUsername);

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/platform/tenants")
                    .header("X-Bootstrap-Token", BOOTSTRAP_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    ProvisionTenantApiResponse resp =
        objectMapper.readValue(
            result.getResponse().getContentAsString(), ProvisionTenantApiResponse.class);
    temporaryPassword = resp.temporaryPassword();
    assertThat(temporaryPassword).isNotBlank();
  }

  @Test
  @DisplayName("login com credenciais corretas devolve access + refresh token")
  void login_caminhoFeliz() throws Exception {
    LoginApiResponse login = doLogin(ownerUsername, temporaryPassword);
    assertThat(login.accessToken()).isNotBlank();
    assertThat(login.refreshToken()).startsWith(tenantSlug + ".");
    assertThat(login.tokenType()).isEqualTo("Bearer");
    assertThat(login.mustChangePassword()).isTrue();
  }

  @Test
  @DisplayName("GET /auth/me com Bearer JWT devolve o principal autenticado")
  void me_comJwt() throws Exception {
    LoginApiResponse login = doLogin(ownerUsername, temporaryPassword);

    MvcResult meResult =
        mockMvc
            .perform(
                get("/api/v1/auth/me").header("Authorization", "Bearer " + login.accessToken()))
            .andExpect(status().isOk())
            .andReturn();

    JsonNode me = objectMapper.readTree(meResult.getResponse().getContentAsString());
    assertThat(me.get("username").asText()).isEqualTo(ownerUsername);
    assertThat(me.get("tenantSlug").asText()).isEqualTo(tenantSlug);
    assertThat(me.get("roles").isArray()).isTrue();
    assertThat(me.get("permissions").isArray()).isTrue();
    // OWNER tem várias permissions sensíveis — checa só duas estáveis.
    assertThat(me.get("permissions").toString()).contains("usuario.criar");
  }

  @Test
  @DisplayName("GET /auth/me sem token devolve 401")
  void me_semJwt_devolve401() throws Exception {
    mockMvc.perform(get("/api/v1/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("login com senha incorreta devolve 401 identidade.invalid-credentials")
  void login_senhaErrada_devolve401() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(ownerUsername, "senhaInvalida-AAA-1!")))
        .andExpect(status().isUnauthorized());
  }

  @Test
  @DisplayName("após 10 falhas seguidas no mesmo (tenant,user) o lockout dispara 429")
  void lockout_apos10Falhas_devolve429() throws Exception {
    for (int i = 0; i < 10; i++) {
      mockMvc
          .perform(
              post("/api/v1/auth/login")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(loginBody(ownerUsername, "senhaInvalida-AAA-" + i + "!")))
          .andExpect(status().isUnauthorized());
    }
    // 11ª tentativa — mesmo com a senha correta, está em lockout.
    mockMvc
        .perform(
            post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(loginBody(ownerUsername, temporaryPassword)))
        .andExpect(status().isTooManyRequests())
        .andExpect(
            org.springframework.test.web.servlet.result.MockMvcResultMatchers.header()
                .exists("Retry-After"));
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private LoginApiResponse doLogin(String identifier, String password) throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(loginBody(identifier, password)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readValue(
        result.getResponse().getContentAsString(), LoginApiResponse.class);
  }

  private String loginBody(String identifier, String password) {
    return """
        {"tenantSlug":"%s","identifier":"%s","password":"%s"}
        """
        .formatted(tenantSlug, identifier, password);
  }
}
