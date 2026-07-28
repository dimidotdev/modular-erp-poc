package dev.dimi.erp.identidade.internal.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração tipada do módulo identidade. Bind do prefix {@code erp-poc.security} do {@code
 * application.yml}.
 */
@ConfigurationProperties("erp-poc.security")
public record IdentidadeProperties(Password password, Jwt jwt, Bootstrap bootstrap) {

  public record Password(Argon2 argon2) {
    public record Argon2(
        int saltLength, int hashLength, int parallelism, int memoryKb, int iterations) {}
  }

  public record Jwt(
      String issuer,
      Duration accessTokenTtl,
      Duration refreshTokenTtl,
      Duration refreshIdleTtl,
      Duration stepUpTtl,
      String secret) {}

  /**
   * Bootstrap inicial da plataforma. {@code token} vazio desabilita o endpoint /platform/tenants.
   */
  public record Bootstrap(String token) {}
}
