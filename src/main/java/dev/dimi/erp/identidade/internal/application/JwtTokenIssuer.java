package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.identidade.internal.config.IdentidadeProperties;
import dev.dimi.erp.shared.multitenancy.Tenant;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Emite access tokens JWT (HS256) com claims do Modular ERP POC.
 *
 * <p>Claims emitidos:
 *
 * <ul>
 *   <li>{@code iss}: issuer configurável ({@code erp-poc.security.jwt.issuer})
 *   <li>{@code sub}: {@link UserId} como string UUID
 *   <li>{@code tid}: tenant id como string UUID
 *   <li>{@code tslug}: slug do tenant
 *   <li>{@code roles}: códigos das roles ativas
 *   <li>{@code perms}: união resolvida das permissions
 *   <li>{@code amr}: authentication methods references (ex: {@code ["pwd"]})
 *   <li>{@code sid}: {@link SessionId} para correlação
 *   <li>{@code iat} / {@code exp}: emissão e expiração ({@code accessTokenTtl})
 * </ul>
 *
 * <p>Chave HS256 vem de {@code erp-poc.security.jwt.secret} (mínimo 32 bytes / 256 bits). Migração
 * para RS256 com chave assimétrica fica para v1.1.
 */
@Component
class JwtTokenIssuer {

  private final IdentidadeProperties props;
  private final Environment environment;
  private SecretKey signingKey;

  JwtTokenIssuer(IdentidadeProperties props, Environment environment) {
    this.props = props;
    this.environment = environment;
  }

  @PostConstruct
  void init() {
    String configuredSecret = props.jwt().secret();
    if (configuredSecret == null || configuredSecret.isBlank()) {
      throw new IllegalStateException("erp-poc.security.jwt.secret é obrigatório.");
    }
    boolean localOrTest =
        environment.getActiveProfiles().length > 0
            && Arrays.stream(environment.getActiveProfiles())
                .allMatch(profile -> profile.equals("local") || profile.equals("test"));
    if (!localOrTest && configuredSecret.toUpperCase().contains("CHANGE-ME")) {
      throw new IllegalStateException(
          "O placeholder de erp-poc.security.jwt.secret só é permitido em local/test.");
    }
    byte[] secret = configuredSecret.getBytes(StandardCharsets.UTF_8);
    if (secret.length < 32) {
      throw new IllegalStateException(
          "erp-poc.security.jwt.secret precisa ter no mínimo 32 bytes (256 bits HMAC-SHA256). "
              + "Atual: "
              + secret.length
              + " bytes.");
    }
    this.signingKey = Keys.hmacShaKeyFor(secret);
  }

  /** Emite access token para o usuário/sessão. {@code stepUpAt} null = sem step-up. */
  String issueAccessToken(
      UserId userId,
      String username,
      Tenant tenant,
      SessionId sessionId,
      List<String> roles,
      List<String> permissions,
      List<String> amr,
      Instant stepUpAt) {
    Instant now = Instant.now();
    Duration ttl = props.jwt().accessTokenTtl();
    Instant exp = now.plus(ttl);

    var builder =
        Jwts.builder()
            .issuer(props.jwt().issuer())
            .subject(userId.value().toString())
            .claim("uname", username)
            .claim("tid", tenant.id().toString())
            .claim("tslug", tenant.slug())
            .claim("roles", roles)
            .claim("perms", permissions)
            .claim("amr", amr)
            .claim("sid", sessionId.value().toString())
            .issuedAt(Date.from(now))
            .expiration(Date.from(exp));
    if (stepUpAt != null) {
      builder.claim("sua", stepUpAt.getEpochSecond());
    }
    return builder.signWith(signingKey, Jwts.SIG.HS256).compact();
  }

  Duration accessTokenTtl() {
    return props.jwt().accessTokenTtl();
  }

  /** Acessível ao {@code JwtTokenValidator} no mesmo módulo (mesma chave HS256). */
  SecretKey signingKey() {
    return signingKey;
  }

  String issuer() {
    return props.jwt().issuer();
  }
}
