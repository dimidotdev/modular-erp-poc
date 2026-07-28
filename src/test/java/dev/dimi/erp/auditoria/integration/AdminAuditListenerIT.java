package dev.dimi.erp.auditoria.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.dimi.erp.identidade.internal.web.dto.LoginApiResponse;
import dev.dimi.erp.support.AbstractWebIntegrationTest;
import java.time.Duration;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * IT que prova que o {@code AdminAuditListener} efetivamente persiste em {@code audit.audit_event}
 * quando o evento {@code UserCreatedEvent} é publicado pela criação de usuário via {@code POST
 * /api/v1/admin/users}.
 *
 * <p>{@code audit.audit_event} é cross-tenant (schema global {@code audit}), então a consulta é
 * feita com nome totalmente qualificado — sem mexer no {@code search_path}.
 *
 * <p>O listener roda de forma assíncrona via outbox do Spring Modulith
 * ({@code @ApplicationModuleListener}, commit-then-publish), então usamos {@link Awaitility} para
 * aguardar até 5s pela linha aparecer.
 */
class AdminAuditListenerIT extends AbstractWebIntegrationTest {

  private static final String BOOTSTRAP_TOKEN = "test-bootstrap-token";

  @Autowired JdbcTemplate jdbc;

  @Test
  @DisplayName("POST /admin/users gera linha em audit.audit_event com category=ADMIN")
  void create_user_persiste_audit_admin() throws Exception {
    // 1. Provisiona tenant + faz login do owner.
    String tenantSlug = "aud-adm-" + System.nanoTime();
    String temporaryPassword = provisionTenant(tenantSlug);
    LoginApiResponse owner = doLogin(tenantSlug, "owner", temporaryPassword);

    // 2. Conta linhas com category='ADMIN' antes do POST.
    Integer before =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_event WHERE event_category = ?",
            Integer.class,
            "ADMIN");
    assertThat(before).isNotNull();

    // 3. POST /admin/users — dispara UserCreatedEvent que o AdminAuditListener consome.
    MvcResult res =
        mockMvc
            .perform(
                post("/api/v1/admin/users")
                    .header("Authorization", "Bearer " + owner.accessToken())
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(
                        """
                        {
                          "username": "alice",
                          "email": "alice@example.com",
                          "fullName": "Alice Tester",
                          "password": "SuperSecret#1234",
                          "roles": ["VENDEDOR"],
                          "mustChangePassword": false
                        }
                        """))
            .andExpect(status().isCreated())
            .andReturn();

    String createdUserId =
        objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asText();

    // 4. Aguarda o listener processar o evento (até 5s) e valida que houve incremento.
    Awaitility.await()
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              Integer after =
                  jdbc.queryForObject(
                      "SELECT COUNT(*) FROM audit.audit_event WHERE event_category = ?",
                      Integer.class,
                      "ADMIN");
              assertThat(after).isNotNull().isGreaterThan(before);
            });

    // 5. Spot-check: o evento USER_CREATED deve existir para o id retornado.
    Integer userCreatedRows =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM audit.audit_event "
                + "WHERE event_category = ? AND event_type = ? AND aggregate_id = ?",
            Integer.class,
            "ADMIN",
            "USER_CREATED",
            createdUserId);
    assertThat(userCreatedRows).isEqualTo(1);
  }

  // ----------------------------------------------------------------------
  // Helpers
  // ----------------------------------------------------------------------

  private String provisionTenant(String slug) throws Exception {
    String body =
        """
        {
          "slug": "%s",
          "displayName": "Audit Admin IT Co",
          "legalName": "Audit Admin IT Limitada",
          "cnpj": "%s",
          "plan": "free",
          "ownerUsername": "owner",
          "ownerEmail": "owner@example.com",
          "ownerFullName": "Owner User"
        }
        """
            .formatted(slug, randomCnpj());

    MvcResult prov =
        mockMvc
            .perform(
                post("/api/v1/platform/tenants")
                    .header("X-Bootstrap-Token", BOOTSTRAP_TOKEN)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(body))
            .andExpect(status().isCreated())
            .andReturn();
    return objectMapper
        .readTree(prov.getResponse().getContentAsString())
        .get("temporaryPassword")
        .asText();
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
