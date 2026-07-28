package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseOrderCancelledEvent;
import dev.dimi.erp.compras.PurchaseOrderCreatedEvent;
import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.PurchaseOrderStatus;
import dev.dimi.erp.compras.PurchaseRequestStatus;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.QuotationStatus;
import dev.dimi.erp.compras.internal.domain.PurchaseOrder;
import dev.dimi.erp.compras.internal.domain.PurchaseOrderItem;
import dev.dimi.erp.compras.internal.domain.PurchaseRequest;
import dev.dimi.erp.compras.internal.domain.Quotation;
import dev.dimi.erp.compras.internal.domain.QuotationResponse;
import dev.dimi.erp.compras.internal.persistence.PurchaseOrderItemRepository;
import dev.dimi.erp.compras.internal.persistence.PurchaseOrderRepository;
import dev.dimi.erp.compras.internal.persistence.PurchaseRequestRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sobre {@code purchase_order}: abertura ad-hoc, geração a partir de cotação, envio e
 * cancelamento. Recebimento + criação de movimento INBOUND fica para slice 6.3.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PurchaseOrderService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "purchase_order";

  private final PurchaseOrderRepository orders;
  private final PurchaseOrderItemRepository orderItems;
  private final PurchaseRequestRepository purchaseRequests;
  private final PurchaseNumberSequencer sequencer;
  private final ApplicationEventPublisher events;
  private final QuotationService quotationService;

  @Transactional
  public PurchaseOrderSummary openAdHoc(OpenPurchaseOrderCommand cmd) {
    if (cmd.items() == null || cmd.items().isEmpty()) {
      throw new InvalidPurchaseOperationException(
          "empty-order", "PC sem itens; informe pelo menos um item.");
    }
    PurchaseOrder saved = persistWithRetry(cmd, null, null);
    BigDecimal totalProducts = BigDecimal.ZERO;
    int line = 1;
    List<PurchaseOrderItem> persistedItems = new ArrayList<>();
    for (OpenPurchaseOrderCommand.Item raw : cmd.items()) {
      BigDecimal gross = raw.unitPrice().multiply(raw.quantity());
      BigDecimal discount = raw.discountValue() == null ? BigDecimal.ZERO : raw.discountValue();
      BigDecimal net = gross.subtract(discount);
      persistedItems.add(
          orderItems.save(
              PurchaseOrderItem.create(
                  saved.id().value(),
                  line++,
                  raw.packagingId().value(),
                  raw.quantity(),
                  raw.unitPrice(),
                  discount,
                  net,
                  raw.expectedDelivery())));
      totalProducts = totalProducts.add(net);
    }
    BigDecimal freight = cmd.totalFreight() == null ? BigDecimal.ZERO : cmd.totalFreight();
    saved.updateTotals(totalProducts, freight, totalProducts.add(freight));
    publishCreatedEvent(saved, cmd.actor());
    log.info(
        "purchase-order ad-hoc id={} branch={} supplier={} total={} actor={}",
        saved.id().value(),
        cmd.branchId().value(),
        cmd.supplierId().value(),
        saved.getTotal(),
        cmd.actor().value());
    return PurchaseOrderSummary.of(
        saved, persistedItems.stream().map(PurchaseOrderItemSummary::of).toList());
  }

  @Transactional
  public List<PurchaseOrderSummary> generateFromQuotation(GenerateOrdersFromQuotationCommand cmd) {
    Quotation q = quotationService.load(cmd.quotationId());
    if (q.getStatus() != QuotationStatus.CLOSED) {
      throw new InvalidPurchaseStateException("Quotation", q.getStatus().name(), "generate-orders");
    }
    if (cmd.selections() == null || cmd.selections().isEmpty()) {
      throw new InvalidPurchaseOperationException(
          "empty-selection",
          "Nenhuma seleção informada; escolha pelo menos um fornecedor com respostas.");
    }
    List<PurchaseOrderSummary> generated = new ArrayList<>();
    for (GenerateOrdersFromQuotationCommand.SupplierSelection sel : cmd.selections()) {
      List<QuotationResponse> supplierResponses =
          quotationService.findResponsesBySupplier(cmd.quotationId(), sel.supplierId().value());
      Set<UUID> selectedIds =
          sel.selectedResponseIds() == null
              ? new HashSet<>()
              : new HashSet<>(sel.selectedResponseIds());
      List<QuotationResponse> picked =
          supplierResponses.stream().filter(r -> selectedIds.contains(r.id().value())).toList();
      if (picked.isEmpty()) {
        throw new InvalidPurchaseOperationException(
            "selection-mismatch",
            "Nenhuma response selecionada bate com o fornecedor " + sel.supplierId().value());
      }
      generated.add(buildOrderFromQuotation(q, sel, picked, cmd.actor()));
    }
    if (q.requestIdTyped() != null) {
      PurchaseRequest request = purchaseRequests.findById(q.requestIdTyped().value()).orElse(null);
      if (request != null && request.getStatus() == PurchaseRequestStatus.QUOTING) {
        request.markOrdered();
      }
    }
    return generated;
  }

  @Transactional
  public PurchaseOrderSummary send(PurchaseOrderId orderId, UserId actor) {
    PurchaseOrder o = load(orderId);
    if (o.getStatus() != PurchaseOrderStatus.OPEN) {
      throw new InvalidPurchaseStateException("PurchaseOrder", o.getStatus().name(), "send");
    }
    o.markSent();
    log.info("purchase-order sent id={} actor={}", o.id().value(), actor.value());
    return summary(o);
  }

  @Transactional
  public PurchaseOrderSummary cancel(PurchaseOrderId orderId, String reason, UserId actor) {
    PurchaseOrder o = load(orderId);
    if (o.getStatus() == PurchaseOrderStatus.RECEIVED
        || o.getStatus() == PurchaseOrderStatus.CANCELLED) {
      throw new InvalidPurchaseStateException("PurchaseOrder", o.getStatus().name(), "cancel");
    }
    if (reason == null || reason.isBlank()) {
      throw new InvalidPurchaseOperationException(
          "cancel-reason-required", "Motivo do cancelamento é obrigatório.");
    }
    o.cancel();
    events.publishEvent(
        new PurchaseOrderCancelledEvent(o.id(), currentTenantId(), reason, actor, Instant.now()));
    log.info("purchase-order cancelled id={} actor={}", o.id().value(), actor.value());
    return summary(o);
  }

  @Transactional(readOnly = true)
  public PurchaseOrderSummary findById(PurchaseOrderId orderId) {
    return summary(load(orderId));
  }

  @Transactional(readOnly = true)
  public Page<PurchaseOrderSummary> listByBranchAndStatus(
      BranchId branchId, PurchaseOrderStatus status, Pageable pageable) {
    return orders.findByBranchIdAndStatus(branchId.value(), status, pageable).map(this::summary);
  }

  private PurchaseOrderSummary buildOrderFromQuotation(
      Quotation q,
      GenerateOrdersFromQuotationCommand.SupplierSelection sel,
      List<QuotationResponse> picked,
      UserId actor) {
    int attempts = 0;
    PurchaseOrder saved = null;
    while (saved == null) {
      attempts++;
      try {
        long number = sequencer.nextFor(TABLE, q.branchIdTyped().value());
        saved =
            orders.saveAndFlush(
                PurchaseOrder.open(
                    number,
                    q.branchIdTyped().value(),
                    sel.supplierId().value(),
                    q.id().value(),
                    q.requestIdTyped() == null ? null : q.requestIdTyped().value(),
                    sel.paymentConditionId(),
                    sel.deliveryDateExpected(),
                    sel.notes()));
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug(
            "purchase-order number collision (quotation flow), retrying attempt={}", attempts);
      }
    }
    BigDecimal totalProducts = BigDecimal.ZERO;
    int line = 1;
    List<PurchaseOrderItem> persisted = new ArrayList<>();
    for (QuotationResponse r : picked) {
      if (r.packagingIdTyped() == null) {
        throw new InvalidPurchaseOperationException(
            "response-missing-packaging",
            "Resposta de cotação sem packaging não pode virar item de PC (response="
                + r.id().value()
                + ")");
      }
      BigDecimal gross = r.getUnitPrice().multiply(r.getQuantity());
      BigDecimal net = gross;
      persisted.add(
          orderItems.save(
              PurchaseOrderItem.create(
                  saved.id().value(),
                  line++,
                  r.packagingIdTyped().value(),
                  r.getQuantity(),
                  r.getUnitPrice(),
                  BigDecimal.ZERO,
                  net,
                  sel.deliveryDateExpected())));
      totalProducts = totalProducts.add(net);
    }
    saved.updateTotals(totalProducts, BigDecimal.ZERO, totalProducts);
    publishCreatedEvent(saved, actor);
    log.info(
        "purchase-order from-quotation id={} quotation={} supplier={} total={} actor={}",
        saved.id().value(),
        q.id().value(),
        sel.supplierId().value(),
        saved.getTotal(),
        actor.value());
    return PurchaseOrderSummary.of(
        saved, persisted.stream().map(PurchaseOrderItemSummary::of).toList());
  }

  private PurchaseOrder load(PurchaseOrderId orderId) {
    return orders
        .findById(orderId.value())
        .orElseThrow(() -> new PurchaseOrderNotFoundException(orderId));
  }

  private PurchaseOrderSummary summary(PurchaseOrder o) {
    List<PurchaseOrderItemSummary> snap =
        orderItems.findByOrderIdOrderByLineNumberAsc(o.id().value()).stream()
            .map(PurchaseOrderItemSummary::of)
            .toList();
    return PurchaseOrderSummary.of(o, snap);
  }

  private PurchaseOrder persistWithRetry(
      OpenPurchaseOrderCommand cmd, QuotationId originQuotation, UUID originRequest) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(TABLE, cmd.branchId().value());
        PurchaseOrder o =
            PurchaseOrder.open(
                number,
                cmd.branchId().value(),
                cmd.supplierId().value(),
                cmd.quotationId() == null ? null : cmd.quotationId().value(),
                cmd.requestId() == null ? null : cmd.requestId().value(),
                cmd.paymentConditionId(),
                cmd.deliveryDateExpected(),
                cmd.notes());
        return orders.saveAndFlush(o);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("purchase-order number collision (ad-hoc), retrying attempt={}", attempts);
      }
    }
  }

  private void publishCreatedEvent(PurchaseOrder o, UserId actor) {
    events.publishEvent(
        new PurchaseOrderCreatedEvent(
            o.id(),
            currentTenantId(),
            o.branchIdTyped(),
            o.supplierIdTyped(),
            o.quotationIdTyped(),
            o.getTotal(),
            actor,
            Instant.now()));
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
