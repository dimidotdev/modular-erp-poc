package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.produto.internal.application.RegisterVariantCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Payload do {@code POST /api/v1/produtos/{id}/variants}. */
public record RegisterVariantApiRequest(
    @NotBlank @Size(max = 60) String code,
    @NotBlank @Size(max = 120) String name,
    Boolean asDefault) {

  public RegisterVariantCommand toCommand(ProductId productId, UserId actor) {
    return new RegisterVariantCommand(productId, code, name, Boolean.TRUE.equals(asDefault), actor);
  }
}
