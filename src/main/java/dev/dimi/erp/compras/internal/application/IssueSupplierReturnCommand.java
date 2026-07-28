package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItemId;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.util.List;

/**
 * Devolução a fornecedor (RN-COM-020). Referencia um recebimento; cada {@link Item} aponta para uma
 * linha do recebimento e quantidade a devolver (parcial OK). v1: NFe de devolução é deferida pro
 * slice fiscal — domínio só guarda {@code reason}; emissão de NF-e seguirá em listener.
 */
public record IssueSupplierReturnCommand(
    PurchaseReceiptId receiptId, String reason, String notes, List<Item> items, UserId actor) {

  public record Item(PurchaseReceiptItemId receiptItemId, BigDecimal quantity) {}
}
