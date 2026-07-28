package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.util.UUID;

/** Comando para criar nova packaging (SKU) dentro de uma variant. */
public record RegisterPackagingCommand(
    UUID variantId,
    String sku,
    String barcode,
    UUID commercialUnitId,
    UUID fiscalUnitId,
    BigDecimal conversionFactor,
    boolean asDefault,
    BigDecimal weightKg,
    UserId actor) {}
