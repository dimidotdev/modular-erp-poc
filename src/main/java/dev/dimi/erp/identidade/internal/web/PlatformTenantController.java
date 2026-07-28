package dev.dimi.erp.identidade.internal.web;

import dev.dimi.erp.identidade.internal.application.TenantProvisioner;
import dev.dimi.erp.identidade.internal.web.dto.ProvisionTenantApiRequest;
import dev.dimi.erp.identidade.internal.web.dto.ProvisionTenantApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de plataforma (cross-tenant). v1: apenas provisionamento de tenants, protegido pelo
 * header {@code X-Bootstrap-Token}. Slices futuros adicionam suspensão, terminação, impersonation
 * com role {@code PLATFORM_ADMIN} (usuário real, não token).
 */
@RestController
@RequestMapping("/api/v1/platform/tenants")
@RequiredArgsConstructor
class PlatformTenantController {

  private final TenantProvisioner provisioner;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasRole('PLATFORM_BOOTSTRAP')")
  ProvisionTenantApiResponse provision(@RequestBody @Valid ProvisionTenantApiRequest request) {
    return ProvisionTenantApiResponse.from(provisioner.provision(request.toCommand()));
  }
}
