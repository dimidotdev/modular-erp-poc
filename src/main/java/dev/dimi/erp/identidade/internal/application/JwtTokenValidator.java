package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.SessionId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

/**
 * Valida access tokens JWT emitidos pelo {@link JwtTokenIssuer}.
 *
 * <p>Verifica:
 *
 * <ul>
 *   <li>Assinatura HS256 com a chave configurada
 *   <li>{@code iss} bate com o issuer configurado
 *   <li>{@code exp} não expirou (JJWT já faz nativamente)
 *   <li>Claims obrigatórios presentes (sub, uname, tid, tslug, sid)
 * </ul>
 *
 * <p>Qualquer falha → {@link InvalidJwtException}. Mensagem ao cliente é genérica; motivo
 * específico fica em logs.
 */
@Component
public class JwtTokenValidator {

  private final JwtTokenIssuer issuer;

  public JwtTokenValidator(JwtTokenIssuer issuer) {
    this.issuer = issuer;
  }

  /**
   * Valida o token e retorna claims tipadas. Lança {@link InvalidJwtException} em qualquer falha.
   */
  public JwtClaims validate(String token) {
    Jws<Claims> jws;
    try {
      jws =
          Jwts.parser()
              .verifyWith(issuer.signingKey())
              .requireIssuer(issuer.issuer())
              .build()
              .parseSignedClaims(token);
    } catch (JwtException | IllegalArgumentException e) {
      throw new InvalidJwtException(e);
    }

    Claims claims = jws.getPayload();
    try {
      UserId userId = new UserId(UUID.fromString(claims.getSubject()));
      String username = claims.get("uname", String.class);
      TenantId tenantId = new TenantId(UUID.fromString(claims.get("tid", String.class)));
      String tenantSlug = claims.get("tslug", String.class);
      SessionId sessionId = new SessionId(UUID.fromString(claims.get("sid", String.class)));
      List<String> roles = stringListClaim(claims, "roles");
      List<String> perms = stringListClaim(claims, "perms");
      List<String> amr = stringListClaim(claims, "amr");
      Instant stepUpAt = epochSecondsClaim(claims, "sua");
      return new JwtClaims(
          userId, username, tenantId, tenantSlug, sessionId, roles, perms, amr, stepUpAt);
    } catch (RuntimeException e) {
      // Claim ausente, formato errado, ou validação do record JwtClaims falhou.
      throw new InvalidJwtException(e);
    }
  }

  /**
   * Extrai uma claim que esperamos ser {@code List<String>}. JJWT desserializa via Jackson como
   * {@code List<Object>}; convertemos explicitamente para evitar surpresas de type erasure.
   */
  @SuppressWarnings("unchecked")
  private static List<String> stringListClaim(Claims claims, String name) {
    Object raw = claims.get(name);
    if (raw == null) return List.of();
    if (!(raw instanceof List<?> list)) {
      throw new IllegalArgumentException("claim " + name + " não é uma lista");
    }
    return ((List<Object>) list).stream().map(String::valueOf).toList();
  }

  /**
   * Extrai uma claim numérica representando epoch seconds e converte para {@link Instant}. Retorna
   * {@code null} se a claim está ausente.
   */
  private static Instant epochSecondsClaim(Claims claims, String name) {
    Object raw = claims.get(name);
    if (raw == null) return null;
    if (raw instanceof Number n) {
      return Instant.ofEpochSecond(n.longValue());
    }
    throw new IllegalArgumentException("claim " + name + " não é numérica");
  }
}
