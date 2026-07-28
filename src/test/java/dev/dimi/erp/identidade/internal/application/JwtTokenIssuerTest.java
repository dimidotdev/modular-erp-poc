package dev.dimi.erp.identidade.internal.application;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dimi.erp.identidade.internal.config.IdentidadeProperties;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class JwtTokenIssuerTest {

  @Test
  void rejeitaPlaceholderForaDosProfilesLocais() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("production");

    JwtTokenIssuer issuer =
        new JwtTokenIssuer(properties("CHANGE-ME-LOCAL-ONLY-32-CHARS-MINIMUM"), environment);

    assertThatThrownBy(issuer::init)
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("local/test");
  }

  @Test
  void aceitaPlaceholderNoProfileTest() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("test");
    JwtTokenIssuer issuer =
        new JwtTokenIssuer(properties("CHANGE-ME-LOCAL-ONLY-32-CHARS-MINIMUM"), environment);

    assertThatCode(issuer::init).doesNotThrowAnyException();
  }

  private static IdentidadeProperties properties(String secret) {
    var jwt =
        new IdentidadeProperties.Jwt(
            "modular-erp-poc",
            Duration.ofMinutes(15),
            Duration.ofDays(7),
            Duration.ofHours(24),
            Duration.ofMinutes(5),
            secret);
    return new IdentidadeProperties(null, jwt, null);
  }
}
