package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.ProductId;

/** Comando de atualização parcial de produto. Campos null não são tocados. */
public record UpdateProductCommand(
    ProductId productId,
    String name,
    String description,
    BrandId brandId,
    String ncm,
    String cest,
    String origin,
    UserId actor) {}
