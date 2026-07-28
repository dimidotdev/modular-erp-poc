package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItemId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Comando de recebimento físico (RN-COM-012/013/014/015). Cada {@link Item} aponta para uma linha
 * do PC; {@code quantity} pode ser parcial. {@code unitCost} pode diferir do PC (preço renegociado,
 * frete rateado etc) — slice 6.3 não rateia frete automaticamente; usa o que vier.
 *
 * <p>{@code idempotencyKey} obrigatório, vem do header {@code Idempotency-Key} no controller.
 */
public record ReceivePurchaseOrderCommand(
    PurchaseOrderId orderId,
    String supplierInvoiceKey,
    String supplierInvoiceNumber,
    UUID dfeDocumentId,
    BigDecimal freightValue,
    String notes,
    List<Item> items,
    String idempotencyKey,
    UserId actor,
    boolean allowExcess) {

  public record Item(
      PurchaseOrderItemId orderItemId,
      BigDecimal quantity,
      BigDecimal unitCost,
      StockLocationId locationId,
      StockBatchId batchId) {}
}
