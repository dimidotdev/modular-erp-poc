package dev.dimi.erp.shared.modularization.internal;

import dev.dimi.erp.shared.modularization.ModuleAccessDeniedException;
import dev.dimi.erp.shared.modularization.ModuleCatalog;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter que bloqueia requisições para módulos desligados no tenant atual.
 *
 * <p>Roda <b>após</b> {@code JwtAuthenticationFilter} (que popula {@link TenantContext}) e antes do
 * {@code AuthorizationFilter} do Spring Security; sem tenant no contexto, deixa passar — outros
 * filters cuidam do 401. Endpoints {@code /api/v1/tenants/me/modules*} são whitelist (do contrário
 * o usuário ficaria preso quando o módulo de configuração estivesse na rota bloqueada).
 *
 * <p>Tradução para HTTP é feita via {@link ModuleAccessDeniedException} → 404 (deliberado: não
 * vazar para o cliente a existência de funcionalidade não contratada). O {@code
 * GlobalExceptionHandler} já registra essa subclasse de {@code DomainException}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 100)
@Slf4j
public class ModuleAccessFilter extends OncePerRequestFilter {

  private static final String API_PREFIX = "/api/v1/";
  private static final String MODULE_CONFIG_PREFIX = "/api/v1/tenants/me/modules";

  private final ModuleAccessService accessService;

  public ModuleAccessFilter(ModuleAccessService accessService) {
    this.accessService = accessService;
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest req, @NonNull HttpServletResponse res, @NonNull FilterChain chain)
      throws ServletException, IOException {
    String path = req.getRequestURI();

    if (!path.startsWith(API_PREFIX) || path.startsWith(MODULE_CONFIG_PREFIX)) {
      chain.doFilter(req, res);
      return;
    }

    Optional<Tenant> tenantOpt = TenantContext.current();
    if (tenantOpt.isEmpty()) {
      // Sem tenant ainda — endpoints públicos/anônimos. O Spring Security cuida do 401 quando
      // for o caso. Aqui não há módulo para resolver pelo path tenant-scoped.
      chain.doFilter(req, res);
      return;
    }

    String tenantKey = tenantOpt.get().slug();
    Optional<ModuleCatalog.ModuleDef> blocked = accessService.blockedModuleForPath(tenantKey, path);
    if (blocked.isPresent()) {
      log.debug(
          "module gating: tenant={} path={} blocked-by={}", tenantKey, path, blocked.get().code());
      throw new ModuleAccessDeniedException(blocked.get().code());
    }
    chain.doFilter(req, res);
  }
}
