package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.ProductId;

/**
 * Comando para anexar uma nova variant ao produto. {@code asDefault=true} promove esta como default
 * (e rebaixa a anterior — RN-PRD-003).
 */
public record RegisterVariantCommand(
    ProductId productId, String code, String name, boolean asDefault, UserId actor) {}
