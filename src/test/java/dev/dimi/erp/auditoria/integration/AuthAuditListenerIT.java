package dev.dimi.erp.auditoria.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Prova end-to-end que os listeners do módulo {@code auditoria} consomem eventos do {@code
 * identidade} via outbox do Spring Modulith e persistem em {@code audit.audit_event}.
 *
 * <p>Cobre três categorias:
 *
 * <ul>
 *   <li>{@link dev.dimi.erp.auditoria.AuditCategory#PLATFORM_ADMIN} — disparado pelo
 *       provisionamento de tenant.
 *   <li>{@link dev.dimi.erp.auditoria.AuditCategory#AUTH} — disparado por {@code LOGIN_SUCCEEDED}.
 *   <li>{@link dev.dimi.erp.auditoria.AuditCategory#ADMIN} — disparado por {@code USER_CREATED} via
 *       {@code POST /api/v1/admin/users}.
 * </ul>
 *
 * <p>Como os listeners são {@link org.springframework.modulith.events.ApplicationModuleListener}
 * (commit-then-publish, transação separada), a escrita é assíncrona — todos os asserts usam
 * Awaitility com timeout de 5s.
 *
 * <p>{@code audit.audit_event} vive em schema global ({@code audit}), fora do {@code search_path}
 * do tenant. Todas as queries qualificam o schema explicitamente.
 */
class AuthAuditListenerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("provisionar tenant grava ao menos 1 evento em PLATFORM_ADMIN")
  void platformAdmin_listener_persistsAuditEvent() throws Exception {
    Integer before =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_event WHERE event_category = 'PLATFORM_ADMIN'",
            Integer.class);
    int initial = before == null ? 0 : before;

    String slug = "pa-" + System.nanoTime();
    provisionTenant(slug);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              Integer now =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit.audit_event WHERE event_category = 'PLATFORM_ADMIN'",
                      Integer.class);
              assertThat(now).isNotNull().isGreaterThan(initial);
            });
  }

  @Test
  @DisplayName("login bem-sucedido grava LOGIN_SUCCEEDED em AUTH para o tenant")
  void auth_listener_persistsLoginSucceeded() throws Exception {
    String slug = "auth-" + System.nanoTime();
    ProvisionInfo prov = provisionTenant(slug);
    UUID tenantUuid = prov.tenantId;

    Integer before =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_event "
                + "WHERE tenant_id = ? AND event_category = 'AUTH'",
            Integer.class,
            tenantUuid);
    int initial = before == null ? 0 : before;

    doLogin(slug, "owner", prov.temporaryPassword);

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              Integer success =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit.audit_event "
                          + "WHERE tenant_id = ? AND event_category = 'AUTH' "
                          + "AND event_type = 'LOGIN_SUCCEEDED'",
                      Integer.class,
                      tenantUuid);
              assertThat(success).isNotNull().isGreaterThanOrEqualTo(1);

              Integer total =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit.audit_event "
                          + "WHERE tenant_id = ? AND event_category = 'AUTH'",
                      Integer.class,
                      tenantUuid);
              assertThat(total).isNotNull().isGreaterThan(initial);
            });
  }

  @Test
  @DisplayName("POST /admin/users grava USER_CREATED em ADMIN para o tenant")
  void admin_listener_persistsUserCreated() throws Exception {
    String slug = "adm-" + System.nanoTime();
    ProvisionInfo prov = provisionTenant(slug);
    UUID tenantUuid = prov.tenantId;

    LoginApiResponse login = doLogin(slug, "owner", prov.temporaryPassword);

    Integer before =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_event "
                + "WHERE tenant_id = ? AND event_category = 'ADMIN'",
            Integer.class,
            tenantUuid);
    int initial = before == null ? 0 : before;

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "vendedor-aud",
                      "email": "vendedor-aud@example.com",
                      "fullName": "Vendedor Auditado",
                      "password": "Senha-Forte-V3ndedor!",
                      "roles": ["VENDEDOR"]
                    }
                    """))
        .andExpect(status().isCreated());

    await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              Integer admin =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit.audit_event "
                          + "WHERE tenant_id = ? AND event_category = 'ADMIN'",
                      Integer.class,
                      tenantUuid);
              assertThat(admin).isNotNull().isGreaterThan(initial);
            });
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private record ProvisionInfo(UUID tenantId, String temporaryPassword) {}

  private ProvisionInfo provisionTenant(String slug) throws Exception {
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Audit IT Co",
          "legalName": "Audit IT Limitada",
          "cnpj": "%s",
          "plan": "free",
          "ownerUsername": "owner",
          "ownerEmail": "owner@example.com",
          "ownerFullName": "Owner User"
        }
        """
            .formatted(slug, randomCnpj());

    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/platform/tenants")
                    .header("X-Bootstrap-Token", BOOTSTRAP_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();

    JsonNode resp = objectMapper.readTree(result.getResponse().getContentAsString());
    UUID tenantId = UUID.fromString(resp.get("tenantId").asText());
    String temporaryPassword = resp.get("temporaryPassword").asText();
    assertThat(temporaryPassword).isNotBlank();
    return new ProvisionInfo(tenantId, temporaryPassword);
  }

  private LoginApiResponse doLogin(String slug, String identifier, String password)
      throws Exception {
    MvcResult result =
        mockMvc
            .perform(
                post("/api/v1/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {"tenantSlug":"%s","identifier":"%s","password":"%s"}
                        """
                            .formatted(slug, identifier, password)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readValue(
        result.getResponse().getContentAsString(), LoginApiResponse.class);
  }
}
