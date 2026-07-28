package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.SupplierReturnId;
import dev.dimi.erp.compras.SupplierReturnIssuedEvent;
import dev.dimi.erp.compras.internal.domain.PurchaseOrder;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItem;
import dev.dimi.erp.compras.internal.domain.PurchaseReceipt;
import dev.dimi.erp.compras.internal.domain.PurchaseReceiptItem;
import dev.dimi.erp.compras.internal.domain.SupplierReturn;
import dev.dimi.erp.compras.internal.domain.SupplierReturnItem;
import dev.dimi.erp.compras.internal.persistence.PurchaseReceiptRepository;
import dev.dimi.erp.compras.internal.persistence.SupplierReturnItemRepository;
import dev.dimi.erp.compras.internal.persistence.SupplierReturnRepository;
import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Emite devolução a fornecedor (RN-COM-020/021). Para cada linha:
 *
 * <ol>
 *   <li>Valida que o item pertence ao recebimento e a quantidade não excede o recebido líquido (já
 *       considerando devoluções prévias).
 *   <li>Chama {@code StockInventoryPort.ship} (OUTBOUND, com origin_type=supplier_return) usando o
 *       location/batch originais do recebimento.
 *   <li>Persiste linha da devolução guardando o {@code unitCost} do recebimento (base do estorno de
 *       MVF no slice financeiro).
 * </ol>
 *
 * <p>NF-e de devolução e estorno do MVF a pagar serão consumidos via listener do {@link
 * SupplierReturnIssuedEvent} quando fiscal/financeiro existirem.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssueSupplierReturnService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "supplier_return";
  private static final String OUTBOUND_ORIGIN_TYPE = "supplier_return";

  private final SupplierReturnRepository supplierReturns;
  private final SupplierReturnItemRepository supplierReturnItems;
  private final PurchaseReceiptRepository receipts;
  private final PurchaseNumberSequencer sequencer;
  private final StockInventoryPort inventory;
  private final ReceivePurchaseOrderService receiveService;
  private final ApplicationEventPublisher events;

  @Transactional
  public SupplierReturnSummary issue(IssueSupplierReturnCommand cmd) {
    if (cmd.reason() == null || cmd.reason().isBlank()) {
      throw new InvalidPurchaseOperationException(
          "return-reason-required", "Motivo da devolução a fornecedor é obrigatório (RN-COM-020).");
    }
    if (cmd.items() == null || cmd.items().isEmpty()) {
      throw new InvalidPurchaseOperationException(
          "empty-return", "Devolução sem itens; informe pelo menos uma linha.");
    }
    Map<UUID, PurchaseReceiptItem> receiptItemsByPk = loadReceiptItems(cmd);
    ensureItemsBelongToReceipt(receiptItemsByPk, cmd.receiptId());
    PurchaseReceipt receipt = loadReceipt(cmd.receiptId());
    SupplierReturn ret = persistHeader(receipt, cmd);

    BigDecimal total = BigDecimal.ZERO;
    for (IssueSupplierReturnCommand.Item raw : cmd.items()) {
      PurchaseReceiptItem rcptItem = receiptItemsByPk.get(raw.receiptItemId().value());
      validateQuantityAvailable(rcptItem, raw.quantity());
      registerOutboundLine(rcptItem, raw, ret, cmd);
      total = total.add(raw.quantity().multiply(rcptItem.getUnitCost()));
    }
    ret.updateTotal(total);

    PurchaseOrder order = receiveService.loadOrder(receipt.orderIdTyped());
    events.publishEvent(
        new SupplierReturnIssuedEvent(
            ret.id(),
            receipt.id(),
            currentTenantId(),
            order.supplierIdTyped(),
            ret.getTotal(),
            cmd.reason(),
            cmd.actor(),
            Instant.now()));
    log.info(
        "supplier-return issued id={} receipt={} total={} actor={}",
        ret.id().value(),
        receipt.id().value(),
        ret.getTotal(),
        cmd.actor().value());

    return summary(ret);
  }

  @Transactional(readOnly = true)
  public SupplierReturnSummary findById(SupplierReturnId id) {
    SupplierReturn r =
        supplierReturns
            .findById(id.value())
            .orElseThrow(
                () ->
                    new InvalidPurchaseOperationException(
                        "supplier-return-not-found",
                        "Devolução a fornecedor não encontrada: " + id.value()));
    return summary(r);
  }

  private Map<UUID, PurchaseReceiptItem> loadReceiptItems(IssueSupplierReturnCommand cmd) {
    Map<UUID, PurchaseReceiptItem> map = new HashMap<>();
    for (IssueSupplierReturnCommand.Item raw : cmd.items()) {
      PurchaseReceiptItem item = receiveService.loadReceiptItem(raw.receiptItemId().value());
      map.put(item.id().value(), item);
    }
    return map;
  }

  private void ensureItemsBelongToReceipt(
      Map<UUID, PurchaseReceiptItem> items, PurchaseReceiptId expected) {
    for (PurchaseReceiptItem i : items.values()) {
      if (!expected.value().equals(i.receiptIdTyped().value())) {
        throw new InvalidPurchaseOperationException(
            "receipt-mismatch",
            "Item " + i.id().value() + " não pertence ao recebimento " + expected.value());
      }
    }
  }

  private PurchaseReceipt loadReceipt(PurchaseReceiptId receiptId) {
    return receipts
        .findById(receiptId.value())
        .orElseThrow(() -> new PurchaseReceiptNotFoundException(receiptId));
  }

  private void validateQuantityAvailable(PurchaseReceiptItem rcptItem, BigDecimal qty) {
    BigDecimal alreadyReturned =
        supplierReturnItems.findByReceiptItemId(rcptItem.id().value()).stream()
            .map(SupplierReturnItem::getQuantity)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal available = rcptItem.getQuantity().subtract(alreadyReturned);
    if (qty.compareTo(available) > 0) {
      throw new InvalidPurchaseOperationException(
          "return-exceeds-received",
          "Quantidade a devolver (%s) excede o disponível (%s) na linha de recebimento %s."
              .formatted(qty, available, rcptItem.id().value()));
    }
  }

  private void registerOutboundLine(
      PurchaseReceiptItem rcptItem,
      IssueSupplierReturnCommand.Item raw,
      SupplierReturn ret,
      IssueSupplierReturnCommand cmd) {
    PurchaseOrderItem orderItem = receiveService.loadOrderItem(rcptItem.orderItemIdTyped());
    PurchaseOrder order = receiveService.loadOrder(orderItem.orderIdTyped());

    String idemKey = "supplier_return:" + ret.id().value() + ":" + rcptItem.id().value();
    inventory.reserve(
        new StockInventoryPort.ReserveCommand(
            orderItem.packagingIdTyped(),
            order.branchIdTyped(),
            rcptItem.locationIdTyped(),
            rcptItem.batchIdTyped(),
            raw.quantity(),
            OUTBOUND_ORIGIN_TYPE,
            ret.id().value(),
            null,
            cmd.actor()));
    inventory.ship(
        new StockInventoryPort.ShipCommand(
            orderItem.packagingIdTyped(),
            order.branchIdTyped(),
            rcptItem.locationIdTyped(),
            rcptItem.batchIdTyped(),
            raw.quantity(),
            OUTBOUND_ORIGIN_TYPE,
            ret.id().value(),
            idemKey,
            cmd.actor()));

    supplierReturnItems.save(
        SupplierReturnItem.create(
            ret.id().value(), rcptItem.id().value(), raw.quantity(), rcptItem.getUnitCost()));
  }

  private SupplierReturn persistHeader(PurchaseReceipt receipt, IssueSupplierReturnCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextForParent(TABLE, "receipt_id", receipt.id().value());
        SupplierReturn ret =
            SupplierReturn.issue(receipt.id().value(), number, cmd.reason(), cmd.notes());
        return supplierReturns.saveAndFlush(ret);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("supplier-return number collision, retrying attempt={}", attempts);
      }
    }
  }

  private SupplierReturnSummary summary(SupplierReturn r) {
    List<SupplierReturnItemSummary> items =
        supplierReturnItems.findBySupplierReturnId(r.id().value()).stream()
            .map(SupplierReturnItemSummary::of)
            .toList();
    return SupplierReturnSummary.of(r, items);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
