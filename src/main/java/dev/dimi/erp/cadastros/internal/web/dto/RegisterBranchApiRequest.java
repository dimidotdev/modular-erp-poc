package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.internal.application.RegisterBranchCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Request body do {@code POST /api/v1/cadastros/branches}. */
public record RegisterBranchApiRequest(
    @NotBlank @Size(max = 20) String code,
    @NotBlank @Size(max = 120) String name,
    Boolean isMain,
    UUID addressId) {

  public RegisterBranchCommand toCommand(UserId registeredBy) {
    return new RegisterBranchCommand(code, name, isMain != null && isMain, addressId, registeredBy);
  }
}
