package dev.dimi.erp.auditoria.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.identidade.internal.web.dto.ProvisionTenantApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT que prova que os listeners do módulo Auditoria efetivamente persistem em {@code
 * audit.audit_event} para os três fluxos cross-module que disparam audit no slice atual:
 *
 * <ul>
 *   <li>{@code PLATFORM_ADMIN} — disparado por {@code TenantProvisionedEvent}.
 *   <li>{@code AUTH} — disparado pelo login bem-sucedido do OWNER.
 *   <li>{@code ADMIN} — disparado pela criação de usuário via {@code POST /api/v1/admin/users}.
 * </ul>
 *
 * <p>{@code audit.audit_event} vive no schema global {@code audit} — fora do search_path
 * multi-tenant — então as queries são totalmente qualificadas ({@code FROM audit.audit_event}),
 * exatamente como faz o {@code AuditWriter}.
 *
 * <p>Como os listeners são {@code @ApplicationModuleListener} (assíncronos via outbox in-process),
 * todas as asserções usam {@link Awaitility} com timeout curto.
 */
class PlatformAdminAuditListenerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  @Autowired JdbcTemplate jdbc;

  // ---------------------------------------------------------------------------
  // (a) PLATFORM_ADMIN
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("provisionar tenant grava evento PLATFORM_ADMIN em audit.audit_event")
  void provisionTenant_gravaAuditEventPlatformAdmin() throws Exception {
    int initial =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_event WHERE event_category = ?",
            Integer.class,
            "PLATFORM_ADMIN");

    UUID tenantId = provisionTenant("padm-" + System.nanoTime()).tenantId().value();

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Integer total =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit.audit_event WHERE event_category = ?",
                      Integer.class,
                      "PLATFORM_ADMIN");
              assertThat(total).isGreaterThan(initial);

              Integer paraTenant =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit.audit_event "
                          + "WHERE event_category = ? AND tenant_id = ?",
                      Integer.class,
                      "PLATFORM_ADMIN",
                      tenantId);
              assertThat(paraTenant).isGreaterThanOrEqualTo(1);
            });
  }

  // ---------------------------------------------------------------------------
  // (b) AUTH
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("login bem-sucedido grava evento AUTH em audit.audit_event")
  void login_gravaAuditEventAuth() throws Exception {
    String slug = "audi-" + System.nanoTime();
    ProvisionTenantApiResponse prov = provisionTenant(slug);
    UUID tenantId = prov.tenantId().value();

    int initial =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_event "
                + "WHERE event_category = ? AND tenant_id = ?",
            Integer.class,
            "AUTH",
            tenantId);

    doLogin(slug, prov.ownerUsername(), prov.temporaryPassword());

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Integer total =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit.audit_event "
                          + "WHERE event_category = ? AND tenant_id = ?",
                      Integer.class,
                      "AUTH",
                      tenantId);
              assertThat(total).isGreaterThan(initial);
            });
  }

  // ---------------------------------------------------------------------------
  // (c) ADMIN
  // ---------------------------------------------------------------------------

  @Test
  @DisplayName("POST /admin/users grava evento ADMIN em audit.audit_event")
  void criarUsuario_gravaAuditEventAdmin() throws Exception {
    String slug = "adma-" + System.nanoTime();
    ProvisionTenantApiResponse prov = provisionTenant(slug);
    UUID tenantId = prov.tenantId().value();

    LoginApiResponse login = doLogin(slug, prov.ownerUsername(), prov.temporaryPassword());

    int initial =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_event "
                + "WHERE event_category = ? AND tenant_id = ?",
            Integer.class,
            "ADMIN",
            tenantId);

    mockMvc
        .perform(
            post("/api/v1/admin/users")
                .header("Authorization", "Bearer " + login.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
                    {
                      "username": "novoUser",
                      "email": "novo@example.com",
                      "fullName": "Novo Usuario",
                      "password": "S3nhaForteParaTeste-X",
                      "roles": ["VENDEDOR"],
                      "mustChangePassword": true
                    }
                    """))
        .andExpect(status().isCreated());

    Awaitility.await()
        .atMost(5, TimeUnit.SECONDS)
        .untilAsserted(
            () -> {
              Integer total =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit.audit_event "
                          + "WHERE event_category = ? AND tenant_id = ?",
                      Integer.class,
                      "ADMIN",
                      tenantId);
              assertThat(total).isGreaterThan(initial);
            });
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private ProvisionTenantApiResponse provisionTenant(String slug) throws Exception {
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

    return objectMapper.readValue(
        result.getResponse().getContentAsString(), ProvisionTenantApiResponse.class);
  }

  private LoginApiResponse doLogin(String tenantSlug, String identifier, String password)
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
                            .formatted(tenantSlug, identifier, password)))
            .andExpect(status().isOk())
            .andReturn();
    return objectMapper.readValue(
        result.getResponse().getContentAsString(), LoginApiResponse.class);
  }
}
