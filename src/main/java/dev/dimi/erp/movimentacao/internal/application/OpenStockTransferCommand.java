package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.List;

/** Comando de abertura de transferência entre filiais (ou locais). */
public record OpenStockTransferCommand(
    BranchId originBranchId,
    BranchId destBranchId,
    StockLocationId originLocationId,
    StockLocationId destLocationId,
    String notes,
    List<Item> items,
    UserId actor) {

  public record Item(PackagingId packagingId, BigDecimal quantity) {}
}
