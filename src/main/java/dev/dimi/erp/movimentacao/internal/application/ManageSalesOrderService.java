package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.movimentacao.SalesOrderApprovedEvent;
import dev.dimi.erp.movimentacao.SalesOrderCreatedEvent;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.SalesOrderStatus;
import dev.dimi.erp.movimentacao.internal.domain.SalesOrder;
import dev.dimi.erp.movimentacao.internal.domain.SalesOrderItem;
import dev.dimi.erp.movimentacao.internal.persistence.SalesOrderItemRepository;
import dev.dimi.erp.movimentacao.internal.persistence.SalesOrderRepository;
import dev.dimi.erp.produto.PriceLookupPort;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de {@code sales_order} + transições de status (submit, approve, reject). Conversão para
 * venda fica em {@link ConvertOrderToSaleService}.
 *
 * <p>Pedido NÃO afeta estoque/financeiro (RN-MOV-001). Aqui só:
 *
 * <ul>
 *   <li>Geração de número sequencial por filial via {@link DocumentNumberSequencer} com retry em
 *       colisão UNIQUE.
 *   <li>Cálculo de totais via {@link LineCalculator}.
 *   <li>Preenchimento opcional de {@code unitPrice} via {@link PriceLookupPort} quando não
 *       informado no comando.
 *   <li>Transições de status enforcement.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManageSalesOrderService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "sales_order";

  private final SalesOrderRepository orders;
  private final SalesOrderItemRepository items;
  private final DocumentNumberSequencer sequencer;
  private final PriceLookupPort priceLookup;
  private final ApplicationEventPublisher events;

  // ---------------------------------------------------------------------------
  // Create
  // ---------------------------------------------------------------------------

  @Transactional
  public SalesOrderSummary register(RegisterSalesOrderCommand cmd) {
    SalesOrder saved = persistWithRetry(cmd);

    List<SalesOrderItem> savedItems = persistItems(saved, cmd.items());
    recalculateAndSave(saved, savedItems);

    events.publishEvent(
        new SalesOrderCreatedEvent(
            saved.id(),
            currentTenantId(),
            cmd.branchId(),
            cmd.customerId(),
            saved.getNumber(),
            saved.getTotal(),
            cmd.actor(),
            Instant.now()));
    log.info(
        "sales-order created id={} branch={} number={} actor={}",
        saved.id().value(),
        cmd.branchId().value(),
        saved.getNumber(),
        cmd.actor().value());

    return SalesOrderSummary.of(saved, savedItems.stream().map(SalesOrderItemSummary::of).toList());
  }

  // ---------------------------------------------------------------------------
  // Update (DRAFT/PENDING_APPROVAL)
  // ---------------------------------------------------------------------------

  @Transactional
  public SalesOrderSummary update(UpdateSalesOrderCommand cmd) {
    SalesOrder o = load(cmd.salesOrderId());
    if (o.getStatus() != SalesOrderStatus.DRAFT
        && o.getStatus() != SalesOrderStatus.PENDING_APPROVAL) {
      throw new InvalidSalesOrderStateException(o.getStatus(), "update");
    }
    if (cmd.notes() != null) {
      o.updateNotes(cmd.notes());
    }

    List<SalesOrderItem> currentItems;
    if (cmd.replacesItems()) {
      items.deleteByOrderId(o.id().value());
      currentItems = persistItems(o, cmd.itemsOrEmpty().stream().toList());
    } else {
      currentItems = items.findByOrderIdOrderByLineNumberAsc(o.id().value());
    }
    recalculateAndSave(o, currentItems);

    return SalesOrderSummary.of(o, currentItems.stream().map(SalesOrderItemSummary::of).toList());
  }

  // ---------------------------------------------------------------------------
  // Status transitions
  // ---------------------------------------------------------------------------

  @Transactional
  public SalesOrderSummary submit(SalesOrderId salesOrderId) {
    SalesOrder o = load(salesOrderId);
    if (o.getStatus() != SalesOrderStatus.DRAFT) {
      throw new InvalidSalesOrderStateException(o.getStatus(), "submit");
    }
    if (o.getValidUntil() != null && o.getValidUntil().isBefore(LocalDate.now())) {
      throw new InvalidSaleOperationException(
          "expired-order",
          "Pedido com validade no passado — atualize validUntil antes de submeter.");
    }
    o.submit();
    return summary(o);
  }

  @Transactional
  public SalesOrderSummary approve(
      SalesOrderId salesOrderId, dev.dimi.erp.identidade.UserId actor) {
    SalesOrder o = load(salesOrderId);
    if (o.getStatus() != SalesOrderStatus.PENDING_APPROVAL) {
      throw new InvalidSalesOrderStateException(o.getStatus(), "approve");
    }
    o.approve();
    events.publishEvent(
        new SalesOrderApprovedEvent(o.id(), currentTenantId(), actor, Instant.now()));
    log.info("sales-order approved id={} actor={}", o.id().value(), actor.value());
    return summary(o);
  }

  @Transactional
  public SalesOrderSummary reject(SalesOrderId salesOrderId) {
    SalesOrder o = load(salesOrderId);
    if (o.getStatus() != SalesOrderStatus.PENDING_APPROVAL) {
      throw new InvalidSalesOrderStateException(o.getStatus(), "reject");
    }
    o.reject();
    return summary(o);
  }

  @Transactional
  public SalesOrderSummary cancel(SalesOrderId salesOrderId) {
    SalesOrder o = load(salesOrderId);
    if (o.getStatus() == SalesOrderStatus.CONVERTED) {
      throw new InvalidSalesOrderStateException(o.getStatus(), "cancel");
    }
    o.cancel();
    return summary(o);
  }

  // ---------------------------------------------------------------------------
  // Read
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public SalesOrderSummary findById(SalesOrderId salesOrderId) {
    return summary(load(salesOrderId));
  }

  @Transactional(readOnly = true)
  public Page<SalesOrderSummary> listByBranchAndStatus(
      BranchId branchId, SalesOrderStatus status, Pageable pageable) {
    return orders.findByBranchIdAndStatus(branchId.value(), status, pageable).map(this::summary);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private SalesOrder load(SalesOrderId salesOrderId) {
    return orders
        .findById(salesOrderId.value())
        .orElseThrow(() -> new SalesOrderNotFoundException(salesOrderId));
  }

  /** Persiste o pedido com retry em caso de colisão de {@code number} (UNIQUE branch+number). */
  private SalesOrder persistWithRetry(RegisterSalesOrderCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(TABLE, cmd.branchId().value());
        SalesOrder o =
            SalesOrder.draft(
                cmd.branchId().value(),
                cmd.customerId().value(),
                cmd.salespersonId() == null ? null : cmd.salespersonId().value(),
                cmd.paymentConditionId(),
                cmd.priceTableId() == null ? null : cmd.priceTableId().value(),
                number,
                cmd.validUntil(),
                cmd.notes());
        return orders.saveAndFlush(o);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("sales-order number collision, retrying attempt={}", attempts);
      }
    }
  }

  private List<SalesOrderItem> persistItems(
      SalesOrder order, List<RegisterSalesOrderCommand.Item> rawItems) {
    if (rawItems == null || rawItems.isEmpty()) {
      return List.of();
    }
    int line = 1;
    List<SalesOrderItem> saved = new java.util.ArrayList<>();
    for (RegisterSalesOrderCommand.Item raw : rawItems) {
      BigDecimal unitPrice = raw.unitPrice();
      if (unitPrice == null) {
        unitPrice =
            priceLookup
                .resolve(raw.packagingId(), order.branchIdTyped(), LocalDate.now())
                .map(PriceLookupPort.PriceQuote::salePrice)
                .orElseThrow(
                    () ->
                        new InvalidSaleOperationException(
                            "no-price",
                            "Sem preço autoritativo para a packaging+filial+data; informe unitPrice."));
      }
      BigDecimal gross = LineCalculator.grossValue(raw.quantity(), unitPrice);
      BigDecimal discount = LineCalculator.discountValueFromPct(gross, raw.discountPct());
      BigDecimal net = LineCalculator.netValue(gross, discount);
      saved.add(
          items.save(
              SalesOrderItem.create(
                  order.id().value(),
                  line++,
                  raw.packagingId().value(),
                  raw.quantity(),
                  unitPrice,
                  raw.discountPct() == null ? BigDecimal.ZERO : raw.discountPct(),
                  discount,
                  net)));
    }
    return saved;
  }

  private void recalculateAndSave(SalesOrder order, List<SalesOrderItem> currentItems) {
    BigDecimal gross =
        currentItems.stream()
            .map(i -> LineCalculator.grossValue(i.getQuantity(), i.getUnitPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal discount =
        currentItems.stream()
            .map(SalesOrderItem::getDiscountValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal net =
        currentItems.stream()
            .map(SalesOrderItem::getNetValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    order.updateTotals(gross, discount, net);
  }

  private SalesOrderSummary summary(SalesOrder o) {
    List<SalesOrderItemSummary> snapItems =
        items.findByOrderIdOrderByLineNumberAsc(o.id().value()).stream()
            .map(SalesOrderItemSummary::of)
            .toList();
    return SalesOrderSummary.of(o, snapItems);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
