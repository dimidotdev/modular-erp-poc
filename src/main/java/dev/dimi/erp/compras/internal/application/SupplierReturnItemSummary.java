package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.SupplierReturnId;
import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItemId;
import dev.dimi.erp.compras.internal.domain.SupplierReturnItem;
import dev.dimi.erp.compras.internal.domain.SupplierReturnItemId;
import java.math.BigDecimal;

/** Snapshot de linha de devolução a fornecedor. */
public record SupplierReturnItemSummary(
    SupplierReturnItemId id,
    SupplierReturnId supplierReturnId,
    PurchaseReceiptItemId receiptItemId,
    BigDecimal quantity,
    BigDecimal unitCost) {

  public static SupplierReturnItemSummary of(SupplierReturnItem i) {
    return new SupplierReturnItemSummary(
        i.id(),
        i.supplierReturnIdTyped(),
        i.receiptItemIdTyped(),
        i.getQuantity(),
        i.getUnitCost());
  }
}
