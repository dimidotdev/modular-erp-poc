package dev.dimi.erp.identidade.internal.web.dto;

import dev.dimi.erp.identidade.internal.application.ProvisionTenantCommand;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Request body do endpoint {@code POST /api/v1/platform/tenants}. */
public record ProvisionTenantApiRequest(
    @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(
            regexp = "^[a-z][a-z0-9-]{1,62}[a-z0-9]$",
            message = "slug deve ser kebab-case, começar com letra e terminar com letra/dígito")
        String slug,
    @NotBlank @Size(max = 200) String displayName,
    @NotBlank @Size(max = 200) String legalName,
    @Pattern(regexp = "^[0-9]{14}$", message = "cnpj deve ter 14 dígitos") String cnpj,
    @Size(max = 50) String plan,
    @NotBlank
        @Size(min = 3, max = 64)
        @Pattern(regexp = "^[a-zA-Z][a-zA-Z0-9._-]+$", message = "ownerUsername inválido")
        String ownerUsername,
    @NotBlank @Email String ownerEmail,
    @NotBlank @Size(max = 200) String ownerFullName) {

  public ProvisionTenantCommand toCommand() {
    return new ProvisionTenantCommand(
        slug, displayName, legalName, cnpj, plan, ownerUsername, ownerEmail, ownerFullName);
  }
}
