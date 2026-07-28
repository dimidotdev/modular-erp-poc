package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Comando para abrir PC ad-hoc (sem origem em cotação). Requer permission {@code
 * compra.pedir.sem-cotacao} no controller.
 */
public record OpenPurchaseOrderCommand(
    BranchId branchId,
    PersonId supplierId,
    QuotationId quotationId,
    PurchaseRequestId requestId,
    UUID paymentConditionId,
    LocalDate deliveryDateExpected,
    BigDecimal totalFreight,
    String notes,
    List<Item> items,
    UserId actor) {

  public record Item(
      PackagingId packagingId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue,
      LocalDate expectedDelivery) {}
}
