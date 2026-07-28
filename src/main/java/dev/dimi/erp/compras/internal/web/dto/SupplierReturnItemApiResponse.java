package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.SupplierReturnId;
import dev.dimi.erp.compras.internal.application.SupplierReturnItemSummary;
import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItemId;
import dev.dimi.erp.compras.internal.domain.SupplierReturnItemId;
import java.math.BigDecimal;

/** Response de linha de devolução. */
public record SupplierReturnItemApiResponse(
    SupplierReturnItemId id,
    SupplierReturnId supplierReturnId,
    PurchaseReceiptItemId receiptItemId,
    BigDecimal quantity,
    BigDecimal unitCost) {

  public static SupplierReturnItemApiResponse from(SupplierReturnItemSummary s) {
    return new SupplierReturnItemApiResponse(
        s.id(), s.supplierReturnId(), s.receiptItemId(), s.quantity(), s.unitCost());
  }
}
