package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Principal autenticado no contexto de uma requisição.
 *
 * <p>Construído pelo filter de autenticação (JWT na Etapa 7; bootstrap token em endpoints de
 * plataforma) a partir das claims do token. Disponibilizado a controllers via
 * {@code @AuthenticationPrincipal AuthenticatedUser principal}.
 *
 * <p>Carrega tudo necessário para autorização e para resolver tenant sem nova consulta ao banco —
 * incluindo a união já resolvida de todas as permissions das roles do usuário.
 *
 * @param userId identidade do usuário no tenant
 * @param tenantId identidade do tenant
 * @param tenantSlug slug do tenant (útil em logs, MDC, URLs internas)
 * @param sessionId sessão atual; correlaciona logs, refresh tokens e revogações
 * @param username username em case canônica (lowercase)
 * @param roles códigos das roles ativas (ex: OWNER, MANAGER)
 * @param permissions união resolvida das permissions de todas as roles (ex: {@code nfe.emitir})
 * @param stepUpAt instante de re-autenticação recente; {@code null} se o token foi emitido por
 *     login normal. Consumido por endpoints sensíveis via {@link #hasFreshStepUp(Duration)}.
 */
public record AuthenticatedUser(
    UserId userId,
    TenantId tenantId,
    String tenantSlug,
    SessionId sessionId,
    String username,
    Set<String> roles,
    Set<String> permissions,
    Instant stepUpAt) {

  public AuthenticatedUser {
    if (userId == null) throw new IllegalArgumentException("userId obrigatório");
    if (tenantId == null) throw new IllegalArgumentException("tenantId obrigatório");
    if (tenantSlug == null || tenantSlug.isBlank())
      throw new IllegalArgumentException("tenantSlug obrigatório");
    if (sessionId == null) throw new IllegalArgumentException("sessionId obrigatório");
    if (username == null || username.isBlank())
      throw new IllegalArgumentException("username obrigatório");
    roles = roles == null ? Set.of() : Set.copyOf(roles);
    permissions = permissions == null ? Set.of() : Set.copyOf(permissions);
  }

  public boolean hasPermission(String permission) {
    return permissions.contains(permission);
  }

  public boolean hasRole(String role) {
    return roles.contains(role);
  }

  /**
   * Verdadeiro se o token carrega step-up dentro da janela {@code maxAge} (medida do instante
   * atual). {@code stepUpAt} ausente sempre devolve {@code false}.
   */
  public boolean hasFreshStepUp(Duration maxAge) {
    if (stepUpAt == null) return false;
    return stepUpAt.isAfter(Instant.now().minus(maxAge));
  }
}
