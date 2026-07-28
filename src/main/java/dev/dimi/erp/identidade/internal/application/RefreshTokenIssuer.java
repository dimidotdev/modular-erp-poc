package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.internal.config.IdentidadeProperties;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * Gera e parseia refresh tokens criptograficamente seguros.
 *
 * <p>Formato do token raw: {@code <tenantSlug>.<32B-base64url>}. O slug embutido no próprio token
 * permite resolver o tenant (e portanto o schema) sem campo separado no request. O hash gravado em
 * {@code refresh_token.token_hash} é SHA-256 do token completo (slug + ponto + random), mantendo
 * segurança.
 *
 * <p>Reuse detection (em {@code RefreshTokenService}) compara hash do token apresentado com {@code
 * refresh_token.token_hash}; raw nunca é persistido.
 */
@Component
class RefreshTokenIssuer {

  private static final SecureRandom SECURE_RANDOM = new SecureRandom();
  private static final int RAW_RANDOM_BYTES = 32;
  private static final char SEPARATOR = '.';

  private final IdentidadeProperties props;

  RefreshTokenIssuer(IdentidadeProperties props) {
    this.props = props;
  }

  /** Emite novo token com TTL absoluto configurado, incorporando o {@code tenantSlug}. */
  RawRefreshToken issue(String tenantSlug) {
    byte[] bytes = new byte[RAW_RANDOM_BYTES];
    SECURE_RANDOM.nextBytes(bytes);
    String randomPart = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    String raw = tenantSlug + SEPARATOR + randomPart;
    String hash = sha256(raw);
    Instant expiresAt = Instant.now().plus(props.jwt().refreshTokenTtl());
    return new RawRefreshToken(raw, hash, expiresAt);
  }

  Duration refreshTokenTtl() {
    return props.jwt().refreshTokenTtl();
  }

  Duration refreshIdleTtl() {
    return props.jwt().refreshIdleTtl();
  }

  /** Hash determinístico do raw token para lookup em {@code refresh_token.token_hash}. */
  static String sha256(String input) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-256");
      byte[] hash = md.digest(input.getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(hash);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 não disponível na JVM", e);
    }
  }

  /**
   * Extrai o {@code tenantSlug} do raw token sem validar criptograficamente o conteúdo. Apenas
   * separa pelo {@code SEPARATOR}; o caller deve validar via {@code TenantRepository#findBySlug}.
   *
   * <p>Retorna vazio se o formato não casa com o esperado (defesa contra tokens malformados).
   */
  static Optional<String> extractTenantSlug(String rawToken) {
    if (rawToken == null) return Optional.empty();
    int sep = rawToken.indexOf(SEPARATOR);
    if (sep <= 0 || sep == rawToken.length() - 1) return Optional.empty();
    return Optional.of(rawToken.substring(0, sep));
  }

  /**
   * Tupla com o raw token (entregue UMA vez ao cliente), seu hash (persistido em {@code
   * refresh_token}) e a expiração calculada.
   */
  record RawRefreshToken(String raw, String hash, Instant expiresAt) {}
}
