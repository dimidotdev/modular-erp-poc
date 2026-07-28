package dev.dimi.erp.produto.internal.web.dto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.Classification;
import dev.dimi.erp.produto.internal.application.RegisterProductCommand;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Payload do {@code POST /api/v1/produtos}. Cria produto + variant default e, se {@code
 * defaultPackagingSku} for informado, packaging default na mesma transação.
 */
public record RegisterProductApiRequest(
    @NotBlank @Size(max = 60) String skuRoot,
    @NotBlank @Size(max = 200) String name,
    @Size(max = 2000) String description,
    UUID brandId,
    @NotNull Classification classification,
    @Pattern(regexp = "^[0-9]{8}$", message = "NCM deve ter 8 dígitos") String ncm,
    @Pattern(regexp = "^[0-9]{7}$", message = "CEST deve ter 7 dígitos") String cest,
    @Pattern(regexp = "^[0-8]$", message = "origin deve ser 0-8 conforme tabela NFe") String origin,
    @Size(max = 60) String defaultVariantCode,
    @Size(max = 80) String defaultPackagingSku,
    @Size(max = 60) String defaultPackagingBarcode) {

  public RegisterProductCommand toCommand(UserId actor) {
    return new RegisterProductCommand(
        skuRoot,
        name,
        description,
        brandId == null ? null : new BrandId(brandId),
        classification,
        ncm,
        cest,
        origin,
        defaultVariantCode,
        defaultPackagingSku,
        defaultPackagingBarcode,
        actor);
  }
}
