package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Comando de abertura de venda nova (sem origem em pedido). Quando {@code unitPrice} é null em
 * algum item, service busca via {@code PriceLookupPort}.
 */
public record OpenSaleCommand(
    BranchId branchId,
    PersonId customerId,
    PersonId salespersonId,
    UUID paymentConditionId,
    String idempotencyKey,
    String notes,
    List<Item> items,
    UserId actor) {

  public record Item(
      PackagingId packagingId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue) {}
}
