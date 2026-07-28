package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.produto.internal.application.UpdateProductCommand;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Payload do {@code PATCH /api/v1/produtos/{id}} — todos campos opcionais. */
public record UpdateProductApiRequest(
    @Size(max = 200) String name,
    @Size(max = 2000) String description,
    UUID brandId,
    @Pattern(regexp = "^[0-9]{8}$", message = "NCM deve ter 8 dígitos") String ncm,
    @Pattern(regexp = "^[0-9]{7}$", message = "CEST deve ter 7 dígitos") String cest,
    @Pattern(regexp = "^[0-8]$", message = "origin deve ser 0-8") String origin) {

  public UpdateProductCommand toCommand(ProductId productId, UserId actor) {
    return new UpdateProductCommand(
        productId,
        name,
        description,
        brandId == null ? null : new BrandId(brandId),
        ncm,
        cest,
        origin,
        actor);
  }
}
