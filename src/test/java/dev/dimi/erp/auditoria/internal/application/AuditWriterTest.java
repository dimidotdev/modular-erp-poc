package dev.dimi.erp.auditoria.internal.application;

import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.PreparedStatementSetter;

@ExtendWith(MockitoExtension.class)
class AuditWriterTest {

  @Mock private JdbcTemplate jdbc;
  @Mock private ObjectMapper objectMapper;

  private AuditWriter writer;

  private static final TenantId TENANT = new TenantId(UUID.randomUUID());

  @BeforeEach
  void setUp() {
    writer = new AuditWriter(jdbc, objectMapper);
  }

  @Test
  void write_emiteSqlInsertEAceitaPayloadJson() throws Exception {
    given(objectMapper.writeValueAsString(any(Map.class))).willReturn("{\"key\":\"value\"}");

    AuditEventRecord ev =
        new AuditEventRecord(
            TENANT,
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.now(),
            "LOGIN_SUCCEEDED",
            AuditCategory.AUTH,
            AuditSeverity.INFO,
            "user",
            UUID.randomUUID().toString(),
            "127.0.0.1",
            "test-agent",
            null,
            "Login OK no tenant demo",
            Map.of("key", "value"));

    writer.write(ev);

    verify(jdbc, times(1))
        .update(
            eq(
                """
                INSERT INTO audit.audit_event (
                  id, tenant_id, user_id, session_id, occurred_at,
                  event_type, event_category, severity,
                  aggregate_type, aggregate_id,
                  actor_ip, actor_user_agent, request_id, trace_id, correlation_id,
                  summary, payload
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::inet, ?, ?, ?, ?, ?, ?::jsonb)
                """),
            any(PreparedStatementSetter.class));
    verify(objectMapper).writeValueAsString(any(Map.class));
  }

  @Test
  void write_payloadNuloOuVazio_naoChamaSerializador() {
    AuditEventRecord ev =
        new AuditEventRecord(
            TENANT,
            null,
            null,
            Instant.now(),
            "USER_REACTIVATED",
            AuditCategory.ADMIN,
            AuditSeverity.INFO,
            "user",
            UUID.randomUUID().toString(),
            null,
            null,
            null,
            "Usuário reativado",
            null);

    assertThatNoException().isThrownBy(() -> writer.write(ev));
    verify(jdbc, times(1)).update(any(String.class), any(PreparedStatementSetter.class));
  }

  @Test
  void write_payloadComFalhaSerializacao_naoBloqueiaInsert() throws Exception {
    given(objectMapper.writeValueAsString(any(Map.class)))
        .willThrow(new JsonProcessingException("boom") {});

    AuditEventRecord ev =
        new AuditEventRecord(
            TENANT,
            null,
            null,
            Instant.now(),
            "USER_CREATED",
            AuditCategory.ADMIN,
            AuditSeverity.INFO,
            "user",
            UUID.randomUUID().toString(),
            null,
            null,
            null,
            "Usuário criado",
            Map.of("k", "v"));

    assertThatNoException().isThrownBy(() -> writer.write(ev));
    verify(jdbc, times(1)).update(any(String.class), any(PreparedStatementSetter.class));
  }

  @Test
  void record_summaryAcimaDe500Chars_recusa() {
    String tooLong = "x".repeat(501);
    Instant now = Instant.now();
    assertThatThrownBy(
            () ->
                new AuditEventRecord(
                    TENANT,
                    null,
                    null,
                    now,
                    "X",
                    AuditCategory.SYSTEM,
                    AuditSeverity.INFO,
                    null,
                    null,
                    null,
                    null,
                    null,
                    tooLong,
                    null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("500 chars");
  }
}
