package dev.dimi.erp.shared.modularization.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.shared.modularization.ModuleCatalog;
import dev.dimi.erp.shared.modularization.internal.ModuleAccessService;
import dev.dimi.erp.shared.modularization.internal.TenantModule;
import dev.dimi.erp.shared.modularization.internal.web.dto.ModuleStatusApiResponse;
import dev.dimi.erp.shared.modularization.internal.web.dto.ToggleModuleRequest;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de visualização e configuração do catálogo de módulos do tenant ativo ({@code
 * /api/v1/tenants/me/modules}).
 *
 * <p>{@link #list()} é leitura pública (basta estar autenticado) para que o frontend renderize
 * Configurações &gt; Módulos para qualquer usuário; {@link #toggle(String, ToggleModuleRequest,
 * AuthenticatedUser)} exige {@code tenant.modulo.configurar} e delega para {@link
 * ModuleAccessService#toggle(String, boolean, java.util.UUID)} — validações de core/installed e
 * audit log ficam lá.
 */
@RestController
@RequestMapping("/api/v1/tenants/me/modules")
@RequiredArgsConstructor
class TenantModuleController {

  private final ModuleAccessService accessService;

  @GetMapping
  @PreAuthorize("isAuthenticated()")
  public List<ModuleStatusApiResponse> list() {
    var enabled =
        accessService.listAll().stream()
            .collect(
                java.util.stream.Collectors.toMap(TenantModule::getCode, TenantModule::isEnabled));
    return ModuleCatalog.all().stream()
        .map(
            def ->
                ModuleStatusApiResponse.of(def, enabled.getOrDefault(def.code(), def.installed())))
        .toList();
  }

  @PatchMapping("/{code}")
  @PreAuthorize("hasAuthority('tenant.modulo.configurar')")
  public ModuleStatusApiResponse toggle(
      @PathVariable String code,
      @Valid @RequestBody ToggleModuleRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    var saved = accessService.toggle(code, body.enabled(), principal.userId().value());
    var def = ModuleCatalog.findByCode(code).orElseThrow();
    return ModuleStatusApiResponse.of(def, saved.isEnabled());
  }
}
