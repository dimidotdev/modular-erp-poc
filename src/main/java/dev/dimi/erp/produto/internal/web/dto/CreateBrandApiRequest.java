package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.internal.application.RegisterBrandCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload do {@code POST /api/v1/produtos/brands}. */
public record CreateBrandApiRequest(
    @NotBlank @Size(max = 60) String code, @NotBlank @Size(max = 120) String name) {

  public RegisterBrandCommand toCommand(UserId actor) {
    return new RegisterBrandCommand(code, name, actor);
  }
}
