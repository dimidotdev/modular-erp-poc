package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.PurchaseOrderStatus;
import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.PurchaseReceivedEvent;
import dev.dimi.erp.compras.internal.domain.PurchaseOrder;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItem;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItemId;
import dev.dimi.erp.compras.internal.domain.PurchaseReceipt;
import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItem;
import dev.dimi.erp.compras.internal.persistence.PurchaseOrderItemRepository;
import dev.dimi.erp.compras.internal.persistence.PurchaseOrderRepository;
import dev.dimi.erp.compras.internal.persistence.PurchaseReceiptItemRepository;
import dev.dimi.erp.compras.internal.persistence.PurchaseReceiptRepository;
import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockQueryPort;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registra recebimento físico de PC (RN-COM-012/013/014/015/016). Para cada item recebido:
 *
 * <ol>
 *   <li>Valida pertinência (item bate com a linha do PC e quantidade não excede ordered sem {@code
 *       allowExcess}).
 *   <li>Acumula em {@code purchase_order_item.quantity_received}.
 *   <li>Chama {@code StockInventoryPort.receive} (INBOUND inline + WAC).
 *   <li>Persiste linha do recebimento referenciando o {@code locationId} efetivamente usado.
 * </ol>
 *
 * <p>Idempotência: chave única na coluna {@code idempotency_key} de {@code purchase_receipt} —
 * service primeiro consulta; se já existe, retorna o existente sem efeito colateral.
 *
 * <p>MVF a pagar e NF-e ficam fora do v1; serão consumidos via listener do {@link
 * PurchaseReceivedEvent} quando financeiro/fiscal existirem.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReceivePurchaseOrderService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "purchase_receipt";

  /** Origin type passado pro ledger de estoque (StockMovement.origin_type). */
  private static final String INBOUND_ORIGIN_TYPE = "purchase_receipt";

  private final PurchaseOrderRepository orders;
  private final PurchaseOrderItemRepository orderItems;
  private final PurchaseReceiptRepository receipts;
  private final PurchaseReceiptItemRepository receiptItems;
  private final PurchaseNumberSequencer sequencer;
  private final StockInventoryPort inventory;
  private final StockQueryPort stockQueries;
  private final ApplicationEventPublisher events;

  @Transactional
  public PurchaseReceiptSummary receive(ReceivePurchaseOrderCommand cmd) {
    if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()) {
      throw new InvalidPurchaseOperationException(
          "idempotency-key-required",
          "Idempotency-Key header é obrigatório no POST de recebimento (RN-COM-014).");
    }
    if (cmd.items() == null || cmd.items().isEmpty()) {
      throw new InvalidPurchaseOperationException(
          "empty-receipt", "Recebimento sem itens; informe pelo menos uma linha.");
    }

    return receipts
        .findByIdempotencyKey(cmd.idempotencyKey())
        .map(this::summary)
        .orElseGet(() -> persistReceipt(cmd));
  }

  @Transactional(readOnly = true)
  public PurchaseReceiptSummary findById(PurchaseReceiptId receiptId) {
    return summary(load(receiptId));
  }

  private PurchaseReceiptSummary persistReceipt(ReceivePurchaseOrderCommand cmd) {
    PurchaseOrder order =
        orders
            .findById(cmd.orderId().value())
            .orElseThrow(() -> new PurchaseOrderNotFoundException(cmd.orderId()));
    if (order.getStatus() != PurchaseOrderStatus.SENT
        && order.getStatus() != PurchaseOrderStatus.PARTIALLY_RECEIVED
        && order.getStatus() != PurchaseOrderStatus.OPEN) {
      throw new InvalidPurchaseStateException("PurchaseOrder", order.getStatus().name(), "receive");
    }

    Map<java.util.UUID, PurchaseOrderItem> itemsByPk = new HashMap<>();
    orderItems
        .findByOrderIdOrderByLineNumberAsc(order.id().value())
        .forEach(i -> itemsByPk.put(i.id().value(), i));

    PurchaseReceipt receipt = persistReceiptHeader(cmd, order);

    BigDecimal totalProducts = BigDecimal.ZERO;
    LocationResolver locations = new LocationResolver(order.branchIdTyped());
    for (ReceivePurchaseOrderCommand.Item raw : cmd.items()) {
      PurchaseOrderItem orderItem = requireOrderItem(itemsByPk, raw, order);
      validateNoExcess(orderItem, raw.quantity(), cmd.allowExcess());
      orderItem.addReceived(raw.quantity());
      StockLocationId location = locations.resolve(raw.locationId());
      registerInboundLine(receipt, order, orderItem, raw, location, cmd);
      totalProducts = totalProducts.add(raw.quantity().multiply(raw.unitCost()));
    }

    BigDecimal freight = cmd.freightValue() == null ? BigDecimal.ZERO : cmd.freightValue();
    receipt.updateTotal(totalProducts.add(freight));

    updateOrderStatus(order);

    events.publishEvent(
        new PurchaseReceivedEvent(
            receipt.id(),
            order.id(),
            currentTenantId(),
            order.branchIdTyped(),
            order.supplierIdTyped(),
            order.getPaymentConditionId(),
            receipt.getTotal(),
            cmd.actor(),
            Instant.now()));
    log.info(
        "purchase-receipt registered id={} order={} total={} items={} actor={}",
        receipt.id().value(),
        order.id().value(),
        receipt.getTotal(),
        cmd.items().size(),
        cmd.actor().value());

    return summary(receipt);
  }

  private PurchaseReceipt persistReceiptHeader(
      ReceivePurchaseOrderCommand cmd, PurchaseOrder order) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextForParent(TABLE, "order_id", order.id().value());
        PurchaseReceipt receipt =
            PurchaseReceipt.record(
                order.id().value(),
                number,
                cmd.supplierInvoiceKey(),
                cmd.supplierInvoiceNumber(),
                cmd.dfeDocumentId(),
                cmd.freightValue(),
                cmd.notes(),
                cmd.actor().value(),
                cmd.idempotencyKey());
        return receipts.saveAndFlush(receipt);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("purchase-receipt number collision, retrying attempt={}", attempts);
      }
    }
  }

  private void updateOrderStatus(PurchaseOrder order) {
    List<PurchaseOrderItem> items =
        orderItems.findByOrderIdOrderByLineNumberAsc(order.id().value());
    boolean allFull = items.stream().allMatch(PurchaseOrderItem::isFullyReceived);
    boolean anyReceived = items.stream().anyMatch(i -> i.getQuantityReceived().signum() > 0);
    if (allFull) {
      order.markReceived();
    } else if (anyReceived) {
      order.markPartiallyReceived();
    }
  }

  private PurchaseOrderItem requireOrderItem(
      Map<java.util.UUID, PurchaseOrderItem> itemsByPk,
      ReceivePurchaseOrderCommand.Item raw,
      PurchaseOrder order) {
    PurchaseOrderItem orderItem = itemsByPk.get(raw.orderItemId().value());
    if (orderItem == null) {
      throw new InvalidPurchaseOperationException(
          "order-item-mismatch",
          "Item " + raw.orderItemId().value() + " não pertence ao PC " + order.id().value());
    }
    return orderItem;
  }

  private void validateNoExcess(
      PurchaseOrderItem orderItem, BigDecimal additional, boolean allowExcess) {
    BigDecimal projected = orderItem.getQuantityReceived().add(additional);
    if (projected.compareTo(orderItem.getQuantityOrdered()) > 0 && !allowExcess) {
      throw new InvalidPurchaseOperationException(
          "excess-not-allowed",
          "Recebimento da linha %d excede a quantidade pedida (%s + %s > %s); habilite allow-excess se for intencional."
              .formatted(
                  orderItem.getLineNumber(),
                  orderItem.getQuantityReceived(),
                  additional,
                  orderItem.getQuantityOrdered()));
    }
  }

  private void registerInboundLine(
      PurchaseReceipt receipt,
      PurchaseOrder order,
      PurchaseOrderItem orderItem,
      ReceivePurchaseOrderCommand.Item raw,
      StockLocationId location,
      ReceivePurchaseOrderCommand cmd) {
    String idemKey = cmd.idempotencyKey() + ":" + orderItem.getLineNumber();
    inventory.receive(
        new StockInventoryPort.ReceiveCommand(
            orderItem.packagingIdTyped(),
            order.branchIdTyped(),
            location,
            raw.batchId(),
            raw.quantity(),
            raw.unitCost(),
            INBOUND_ORIGIN_TYPE,
            receipt.id().value(),
            idemKey,
            cmd.actor()));

    receiptItems.save(
        PurchaseReceiptItem.create(
            receipt.id().value(),
            orderItem.id().value(),
            raw.quantity(),
            raw.unitCost(),
            raw.batchId() == null ? null : raw.batchId().value(),
            location.value()));
  }

  /** Resolve location: explícito do request, cacheando o default da filial sob demanda. */
  private final class LocationResolver {
    private final BranchId branchId;
    private StockLocationId cachedDefault;

    LocationResolver(BranchId branchId) {
      this.branchId = branchId;
    }

    StockLocationId resolve(StockLocationId explicit) {
      if (explicit != null) {
        return explicit;
      }
      if (cachedDefault == null) {
        cachedDefault =
            stockQueries
                .findDefaultLocation(branchId)
                .orElseThrow(
                    () ->
                        new InvalidPurchaseOperationException(
                            "no-default-location",
                            "Filial sem local default ativo — informe locationId por item ou crie um StockLocation com asDefault=true."));
      }
      return cachedDefault;
    }
  }

  private PurchaseReceipt load(PurchaseReceiptId receiptId) {
    return receipts
        .findById(receiptId.value())
        .orElseThrow(() -> new PurchaseReceiptNotFoundException(receiptId));
  }

  PurchaseReceiptSummary summary(PurchaseReceipt r) {
    List<PurchaseReceiptItemSummary> items =
        receiptItems.findByReceiptId(r.id().value()).stream()
            .map(PurchaseReceiptItemSummary::of)
            .toList();
    return PurchaseReceiptSummary.of(r, items);
  }

  /** Helper de pacote — usado pelo {@code IssueSupplierReturnService} para localizar itens. */
  PurchaseReceiptItem loadReceiptItem(java.util.UUID receiptItemId) {
    return receiptItems
        .findById(receiptItemId)
        .orElseThrow(
            () ->
                new InvalidPurchaseOperationException(
                    "receipt-item-not-found",
                    "Linha de recebimento não encontrada: " + receiptItemId));
  }

  PurchaseOrderItem loadOrderItem(PurchaseOrderItemId id) {
    return orderItems
        .findById(id.value())
        .orElseThrow(
            () ->
                new InvalidPurchaseOperationException(
                    "order-item-not-found", "Linha de PC não encontrada: " + id.value()));
  }

  PurchaseOrder loadOrder(PurchaseOrderId orderId) {
    return orders
        .findById(orderId.value())
        .orElseThrow(() -> new PurchaseOrderNotFoundException(orderId));
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
