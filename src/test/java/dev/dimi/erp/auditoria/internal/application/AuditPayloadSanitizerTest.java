package dev.dimi.erp.auditoria.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AuditPayloadSanitizerTest {

  @Test
  void redigeSegredosEmEstruturasAninhadasSemApagarMetadados() {
    Map<String, Object> result =
        AuditPayloadSanitizer.sanitize(
            Map.of(
                "username", "alice",
                "password", "do-not-store",
                "temporaryPassword", "do-not-store",
                "nested",
                    List.of(
                        Map.of(
                            "client_secret", "do-not-store",
                            "refreshTokenHash", "do-not-store",
                            "tokensRevoked", 2))));

    assertThat(result.get("username")).isEqualTo("alice");
    assertThat(result.get("password")).isEqualTo(AuditPayloadSanitizer.REDACTED);
    assertThat(result.toString()).doesNotContain("do-not-store");
    assertThat(result.toString()).contains("tokensRevoked=2");
  }
}
