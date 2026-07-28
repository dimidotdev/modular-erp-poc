package dev.dimi.erp.auditoria.internal.web.dto;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.AuditSeverity;
import dev.dimi.erp.auditoria.internal.application.AuditEventView;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AuditEventApiResponseTest {

  @Test
  void redigeMetadadosSensiveisSemOcultarIdentificacaoDoEvento() {
    UUID id = UUID.randomUUID();
    AuditEventView view =
        new AuditEventView(
            id,
            UUID.randomUUID(),
            UUID.randomUUID(),
            UUID.randomUUID(),
            Instant.parse("2026-01-01T00:00:00Z"),
            "USER_UPDATED",
            AuditCategory.ADMIN,
            AuditSeverity.INFO,
            "User",
            "user-1",
            "192.0.2.1",
            "Test Agent",
            UUID.randomUUID(),
            "trace-1",
            UUID.randomUUID(),
            "Usuário atualizado",
            "{\"field\":\"email\"}");

    AuditEventApiResponse response = AuditEventApiResponse.from(view, false);

    assertThat(response.id()).isEqualTo(id);
    assertThat(response.eventType()).isEqualTo("USER_UPDATED");
    assertThat(response.summary()).isEqualTo("Usuário atualizado");
    assertThat(response.actorIp()).isNull();
    assertThat(response.actorUserAgent()).isNull();
    assertThat(response.payload()).isNull();
  }
}
