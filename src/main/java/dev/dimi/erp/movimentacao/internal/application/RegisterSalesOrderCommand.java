package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceTableId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Comando de criação de pedido em DRAFT. Itens são opcionais; podem ser adicionados depois via
 * update. Quando preço unit não é informado por linha, service busca via {@code PriceLookupPort}
 * usando a data atual.
 */
public record RegisterSalesOrderCommand(
    BranchId branchId,
    PersonId customerId,
    PersonId salespersonId,
    UUID paymentConditionId,
    PriceTableId priceTableId,
    LocalDate validUntil,
    String notes,
    List<Item> items,
    UserId actor) {

  public record Item(
      PackagingId packagingId, BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountPct) {}
}
