package dev.dimi.erp.financeiro.internal.web;

import dev.dimi.erp.financeiro.internal.application.ManageTenantSettingService;
import dev.dimi.erp.financeiro.internal.web.dto.TenantSettingApiResponse;
import dev.dimi.erp.financeiro.internal.web.dto.UpdateTenantSettingApiRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de configuração singleton do tenant ({@code /api/v1/financeiro/settings}). Consumido
 * pelo onboarding (configurar contas/CC default antes da 1ª venda) e por scripts de administração.
 */
@RestController
@RequestMapping("/api/v1/financeiro/settings")
@RequiredArgsConstructor
class TenantSettingController {

  private final ManageTenantSettingService service;

  @GetMapping
  @PreAuthorize("hasAuthority('tenant.config.consultar')")
  TenantSettingApiResponse get() {
    return TenantSettingApiResponse.from(service.get());
  }

  @PutMapping
  @PreAuthorize("hasAuthority('tenant.config.editar')")
  TenantSettingApiResponse update(
      @RequestBody @Valid UpdateTenantSettingApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return TenantSettingApiResponse.from(service.update(body.toCommand(principal.userId())));
  }
}
