package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceTableId;
import java.math.BigDecimal;
import java.util.List;

/**
 * Batch upsert de itens de tabela de preço. {@code replaceAll=true} substitui completamente os
 * itens da tabela (delete + insert); {@code replaceAll=false} faz upsert item-a-item preservando o
 * que não estiver no payload. RN-PRD-005: {@code discountMax} em fração [0,1].
 */
public record UpsertPriceItemsCommand(
    PriceTableId priceTableId, List<Item> items, boolean replaceAll, UserId actor) {

  public record Item(PackagingId packagingId, BigDecimal salePrice, BigDecimal discountMax) {}
}
