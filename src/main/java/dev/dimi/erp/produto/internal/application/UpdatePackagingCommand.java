package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.UUID;

/** Comando de atualização parcial de packaging. Campos null não são tocados. */
public record UpdatePackagingCommand(
    PackagingId packagingId,
    String barcode,
    UUID commercialUnitId,
    UUID fiscalUnitId,
    BigDecimal conversionFactor,
    BigDecimal weightKg,
    UserId actor) {}
