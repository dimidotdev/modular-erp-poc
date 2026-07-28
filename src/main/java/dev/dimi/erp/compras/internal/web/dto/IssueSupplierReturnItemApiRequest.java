package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.compras.internal.application.IssueSupplierReturnCommand;
import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItemId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de devolução referenciando uma linha de recebimento. */
public record IssueSupplierReturnItemApiRequest(
    @NotNull UUID receiptItemId, @NotNull @Positive BigDecimal quantity) {

  public IssueSupplierReturnCommand.Item toCommand() {
    return new IssueSupplierReturnCommand.Item(new PurchaseReceiptItemId(receiptItemId), quantity);
  }
}
