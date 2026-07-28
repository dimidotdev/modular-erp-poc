package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Recebimento não encontrado. */
public class PurchaseReceiptNotFoundException extends DomainException {

  private final PurchaseReceiptId receiptId;

  public PurchaseReceiptNotFoundException(PurchaseReceiptId receiptId) {
    super(
        "compras.receipt-not-found",
        HttpStatus.NOT_FOUND,
        "Recebimento não encontrado",
        "Não há recebimento com id=" + receiptId.value());
    this.receiptId = receiptId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("receiptId", receiptId.value());
  }
}
