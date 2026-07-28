package dev.dimi.erp.identidade.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.internal.application.InvalidJwtException;
import dev.dimi.erp.identidade.internal.application.JwtClaims;
import dev.dimi.erp.identidade.internal.application.JwtTokenValidator;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.HashSet;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter de autenticação JWT.
 *
 * <p>Lê {@code Authorization: Bearer <jwt>}, valida via {@link JwtTokenValidator} e popula:
 *
 * <ul>
 *   <li>{@code SecurityContextHolder} com {@link JwtAuthentication} carregando {@link
 *       AuthenticatedUser} como principal — permite {@code @PreAuthorize} e
 *       {@code @AuthenticationPrincipal}.
 *   <li>{@link TenantContext} com o tenant das claims — Hibernate resolve schema automaticamente.
 *   <li>MDC com {@code tenantId}, {@code tenantSlug}, {@code userId}, {@code sessionId} — logs
 *       estruturados ganham correlação.
 * </ul>
 *
 * <p>Ausência de header → segue sem autenticar (Spring Security rejeita downstream com 401 via
 * entry point). JWT presente mas inválido → segue sem autenticar e loga warn.
 *
 * <p>Limpeza: {@code TenantContext} e MDC tenant-scoped são limpos no {@code finally} para que a
 * request seguinte na mesma thread não herde estado. {@code SecurityContext} é limpo pelo Spring
 * Security automaticamente.
 */
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private static final String AUTHORIZATION_HEADER = "Authorization";
  private static final String BEARER_PREFIX = "Bearer ";

  private final JwtTokenValidator validator;

  public JwtAuthenticationFilter(JwtTokenValidator validator) {
    this.validator = validator;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {

    String header = request.getHeader(AUTHORIZATION_HEADER);
    if (header == null || !header.startsWith(BEARER_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }
    String token = header.substring(BEARER_PREFIX.length()).trim();
    if (token.isEmpty()) {
      chain.doFilter(request, response);
      return;
    }

    JwtClaims claims;
    try {
      claims = validator.validate(token);
    } catch (InvalidJwtException e) {
      log.warn("JWT inválido apresentado em {}: {}", request.getRequestURI(), e.getMessage());
      chain.doFilter(request, response);
      return;
    }

    AuthenticatedUser principal =
        new AuthenticatedUser(
            claims.userId(),
            claims.tenantId(),
            claims.tenantSlug(),
            claims.sessionId(),
            claims.username(),
            new HashSet<>(claims.roles()),
            new HashSet<>(claims.permissions()),
            claims.stepUpAt());

    Tenant tenant = new Tenant(claims.tenantId().value(), claims.tenantSlug(), schemaName(claims));
    TenantContext.set(tenant);
    populateMdc(claims);

    SecurityContextHolder.getContext().setAuthentication(new JwtAuthentication(principal));

    try {
      chain.doFilter(request, response);
    } finally {
      TenantContext.clear();
      clearMdcTenantScoped();
    }
  }

  /**
   * Schema do tenant é derivado do slug usando a mesma convenção do {@code TenantProvisioner}:
   * {@code tenant_<slug-com-hifens-para-underscores>}.
   *
   * <p>Isso evita um SELECT em {@code shared.tenant} a cada request — claims já têm tudo. Se um dia
   * precisarmos validar status do tenant em runtime (suspensão imediata), adicionamos uma checagem
   * aqui (provável cache Caffeine).
   */
  private static String schemaName(JwtClaims claims) {
    return "tenant_" + claims.tenantSlug().replace('-', '_');
  }

  private static void populateMdc(JwtClaims claims) {
    MDC.put("tenantId", claims.tenantId().value().toString());
    MDC.put("tenantSlug", claims.tenantSlug());
    MDC.put("userId", claims.userId().value().toString());
    MDC.put("sessionId", claims.sessionId().value().toString());
  }

  private static void clearMdcTenantScoped() {
    MDC.remove("tenantId");
    MDC.remove("tenantSlug");
    MDC.remove("userId");
    MDC.remove("sessionId");
  }
}
