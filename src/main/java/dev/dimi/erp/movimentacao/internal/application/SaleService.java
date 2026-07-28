package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockQueryPort;
import dev.dimi.erp.movimentacao.SaleClosedEvent;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleOpenedEvent;
import dev.dimi.erp.movimentacao.SaleStatus;
import dev.dimi.erp.movimentacao.SalesOrderConvertedEvent;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.SalesOrderStatus;
import dev.dimi.erp.movimentacao.internal.domain.Sale;
import dev.dimi.erp.movimentacao.internal.domain.SaleItem;
import dev.dimi.erp.movimentacao.internal.domain.SalesOrder;
import dev.dimi.erp.movimentacao.internal.domain.SalesOrderItem;
import dev.dimi.erp.movimentacao.internal.persistence.SaleItemRepository;
import dev.dimi.erp.movimentacao.internal.persistence.SaleRepository;
import dev.dimi.erp.movimentacao.internal.persistence.SalesOrderItemRepository;
import dev.dimi.erp.movimentacao.internal.persistence.SalesOrderRepository;
import dev.dimi.erp.produto.PriceLookupPort;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
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
 * Operações sobre {@code sale}:
 *
 * <ul>
 *   <li>{@link #open} cria venda nova em OPEN com itens. Idempotente via {@code idempotencyKey}:
 *       chamada com mesma key retorna a venda já criada.
 *   <li>{@link #convertFromOrder} cria venda nova OPEN a partir de pedido APPROVED (RN-MOV-002).
 *       Marca o pedido como CONVERTED.
 *   <li>{@link #close} finaliza venda OPEN: valida saldo via {@link StockInventoryPort}, baixa
 *       estoque por item (gera OUTBOUND no ledger), trava status como CLOSED e publica {@link
 *       SaleClosedEvent} para financeiro/fiscal consumirem (RN-MOV-004).
 * </ul>
 *
 * <p>v1 (slice 5.2): caminho feliz. Credit limit (RN-MOV-006), desconto máximo (RN-MOV-007), NFe
 * sync (RN-MOV-009), Saga de cancelamento (RN-MOV-010) ficam para slices futuros.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class SaleService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String SALE_TABLE = "sale";

  private final SaleRepository sales;
  private final SaleItemRepository saleItems;
  private final SalesOrderRepository orders;
  private final SalesOrderItemRepository orderItems;
  private final DocumentNumberSequencer sequencer;
  private final PriceLookupPort priceLookup;
  private final StockInventoryPort inventory;
  private final StockQueryPort stockQueries;
  private final ApplicationEventPublisher events;

  // ---------------------------------------------------------------------------
  // Open
  // ---------------------------------------------------------------------------

  @Transactional
  public SaleSummary open(OpenSaleCommand cmd) {
    if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
      var existing = sales.findByIdempotencyKey(cmd.idempotencyKey());
      if (existing.isPresent()) {
        return summary(existing.get());
      }
    }
    Sale saved = persistSaleWithRetry(cmd);
    List<SaleItem> savedItems = persistItemsForOpen(saved, cmd.items());
    recalculateAndSave(saved, savedItems);

    events.publishEvent(
        new SaleOpenedEvent(
            saved.id(),
            currentTenantId(),
            cmd.branchId(),
            cmd.customerId(),
            null,
            saved.getNumber(),
            saved.getTotal(),
            cmd.actor(),
            Instant.now()));
    log.info(
        "sale opened id={} branch={} number={} actor={}",
        saved.id().value(),
        cmd.branchId().value(),
        saved.getNumber(),
        cmd.actor().value());
    return SaleSummary.of(saved, savedItems.stream().map(SaleItemSummary::of).toList());
  }

  // ---------------------------------------------------------------------------
  // Convert from order
  // ---------------------------------------------------------------------------

  @Transactional
  public SaleSummary convertFromOrder(SalesOrderId orderId, dev.dimi.erp.identidade.UserId actor) {
    SalesOrder order =
        orders
            .findById(orderId.value())
            .orElseThrow(() -> new SalesOrderNotFoundException(orderId));
    if (order.getStatus() != SalesOrderStatus.APPROVED) {
      throw new InvalidSalesOrderStateException(order.getStatus(), "convert");
    }
    if (order.getValidUntil() != null && order.getValidUntil().isBefore(LocalDate.now())) {
      throw new InvalidSaleOperationException(
          "expired-order", "Pedido expirado (validUntil < hoje); renove antes de converter.");
    }

    List<SalesOrderItem> rawItems =
        orderItems.findByOrderIdOrderByLineNumberAsc(order.id().value());
    if (rawItems.isEmpty()) {
      throw new InvalidSaleOperationException(
          "empty-order", "Pedido sem itens; adicione itens antes de converter.");
    }

    Sale saved = persistConvertedSaleWithRetry(order, actor);

    List<SaleItem> savedItems = new ArrayList<>();
    int line = 1;
    for (SalesOrderItem oi : rawItems) {
      BigDecimal gross = LineCalculator.grossValue(oi.getQuantity(), oi.getUnitPrice());
      BigDecimal net = LineCalculator.netValue(gross, oi.getDiscountValue());
      savedItems.add(
          saleItems.save(
              SaleItem.create(
                  saved.id().value(),
                  line++,
                  oi.getPackagingId(),
                  oi.getQuantity(),
                  oi.getUnitPrice(),
                  oi.getDiscountValue(),
                  net,
                  null)));
    }
    recalculateAndSave(saved, savedItems);

    order.markConverted(saved.id().value());

    events.publishEvent(
        new SaleOpenedEvent(
            saved.id(),
            currentTenantId(),
            saved.branchIdTyped(),
            saved.customerIdTyped(),
            order.id(),
            saved.getNumber(),
            saved.getTotal(),
            actor,
            Instant.now()));
    events.publishEvent(
        new SalesOrderConvertedEvent(
            order.id(), saved.id(), currentTenantId(), actor, Instant.now()));
    log.info(
        "sales-order converted id={} saleId={} actor={}",
        order.id().value(),
        saved.id().value(),
        actor.value());

    return SaleSummary.of(saved, savedItems.stream().map(SaleItemSummary::of).toList());
  }

  // ---------------------------------------------------------------------------
  // Close — baixa estoque + dispara evento para financeiro/fiscal
  // ---------------------------------------------------------------------------

  @Transactional
  public SaleSummary close(SaleId saleId, dev.dimi.erp.identidade.UserId actor) {
    Sale s = sales.findById(saleId.value()).orElseThrow(() -> new SaleNotFoundException(saleId));
    if (s.getStatus() != SaleStatus.OPEN) {
      throw new InvalidSaleStateException(s.getStatus(), "close");
    }
    List<SaleItem> currentItems = saleItems.findBySaleIdOrderByLineNumberAsc(s.id().value());
    if (currentItems.isEmpty()) {
      throw new InvalidSaleOperationException(
          "empty-sale", "Venda sem itens; adicione itens antes de fechar.");
    }

    // Baixa estoque item-a-item: reserve + ship na mesma transação.
    // RN-MOV-001: pedido v1 não reserva no aceite. RN-MOV-005: ao fechar venda,
    // saldo insuficiente lança estoque.insufficient via StockInventoryService.reserve.
    // ship subsequente consome a reserva e on-hand simultaneamente.
    // Idempotência da PARTE OUTBOUND via key composta sale_id:line_number — reserve
    // não tem idempotency key (é write transacional efêmero que vira ship logo após).
    StockLocationId location = defaultLocationFor(s.branchIdTyped());
    for (SaleItem item : currentItems) {
      String idemKey = "sale:" + s.id().value() + ":" + item.getLineNumber();
      inventory.reserve(
          new StockInventoryPort.ReserveCommand(
              item.packagingIdTyped(),
              s.branchIdTyped(),
              location,
              null,
              item.getQuantity(),
              "sale",
              s.id().value(),
              null,
              actor));
      inventory.ship(
          new StockInventoryPort.ShipCommand(
              item.packagingIdTyped(),
              s.branchIdTyped(),
              location,
              null,
              item.getQuantity(),
              "sale",
              s.id().value(),
              idemKey,
              actor));
    }

    s.close();

    events.publishEvent(
        new SaleClosedEvent(
            s.id(),
            currentTenantId(),
            s.branchIdTyped(),
            s.customerIdTyped(),
            s.getPaymentConditionId(),
            s.getTotalGross(),
            s.getTotalDiscount(),
            s.getTotalFreight(),
            s.getTotal(),
            actor,
            Instant.now()));
    log.info("sale closed id={} number={} actor={}", s.id().value(), s.getNumber(), actor.value());

    return SaleSummary.of(s, currentItems.stream().map(SaleItemSummary::of).toList());
  }

  // ---------------------------------------------------------------------------
  // Read
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public SaleSummary findById(SaleId saleId) {
    Sale s = sales.findById(saleId.value()).orElseThrow(() -> new SaleNotFoundException(saleId));
    return summary(s);
  }

  @Transactional(readOnly = true)
  public Page<SaleSummary> listByBranchAndStatus(
      BranchId branchId, SaleStatus status, Pageable pageable) {
    return sales.findByBranchIdAndStatus(branchId.value(), status, pageable).map(this::summary);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Sale persistSaleWithRetry(OpenSaleCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(SALE_TABLE, cmd.branchId().value());
        Sale s =
            Sale.open(
                cmd.branchId().value(),
                cmd.customerId().value(),
                cmd.salespersonId() == null ? null : cmd.salespersonId().value(),
                null,
                cmd.paymentConditionId(),
                number,
                cmd.idempotencyKey(),
                cmd.notes());
        return sales.saveAndFlush(s);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("sale number collision, retrying attempt={}", attempts);
      }
    }
  }

  private Sale persistConvertedSaleWithRetry(
      SalesOrder order, dev.dimi.erp.identidade.UserId actor) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(SALE_TABLE, order.getBranchId());
        Sale s =
            Sale.open(
                order.getBranchId(),
                order.getCustomerId(),
                order.getSalespersonId(),
                order.id().value(),
                order.getPaymentConditionId(),
                number,
                "order:" + order.id().value(),
                null);
        return sales.saveAndFlush(s);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug(
            "converted sale number collision, retrying attempt={} actor={}",
            attempts,
            actor.value());
      }
    }
  }

  private List<SaleItem> persistItemsForOpen(Sale sale, List<OpenSaleCommand.Item> rawItems) {
    if (rawItems == null || rawItems.isEmpty()) {
      return List.of();
    }
    int line = 1;
    List<SaleItem> saved = new ArrayList<>();
    for (OpenSaleCommand.Item raw : rawItems) {
      BigDecimal unitPrice = raw.unitPrice();
      if (unitPrice == null) {
        unitPrice =
            priceLookup
                .resolve(raw.packagingId(), sale.branchIdTyped(), LocalDate.now())
                .map(PriceLookupPort.PriceQuote::salePrice)
                .orElseThrow(
                    () ->
                        new InvalidSaleOperationException(
                            "no-price",
                            "Sem preço autoritativo para a packaging+filial+data; informe unitPrice."));
      }
      BigDecimal gross = LineCalculator.grossValue(raw.quantity(), unitPrice);
      BigDecimal discount = raw.discountValue() == null ? BigDecimal.ZERO : raw.discountValue();
      BigDecimal net = LineCalculator.netValue(gross, discount);
      saved.add(
          saleItems.save(
              SaleItem.create(
                  sale.id().value(),
                  line++,
                  raw.packagingId().value(),
                  raw.quantity(),
                  unitPrice,
                  discount,
                  net,
                  null)));
    }
    return saved;
  }

  private void recalculateAndSave(Sale sale, List<SaleItem> currentItems) {
    BigDecimal gross =
        currentItems.stream()
            .map(i -> LineCalculator.grossValue(i.getQuantity(), i.getUnitPrice()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal discount =
        currentItems.stream()
            .map(SaleItem::getDiscountValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal net =
        currentItems.stream().map(SaleItem::getNetValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    sale.updateTotals(gross, discount, BigDecimal.ZERO, net);
  }

  /**
   * v1: resolve via {@code StockQueryPort.findDefaultLocation}. Quando endpoint de venda exigir
   * location explícito por item (futuro), trocar por valor vindo do comando — todos os items da
   * venda v1 baixam do mesmo local default da filial.
   */
  private StockLocationId defaultLocationFor(BranchId branchId) {
    return stockQueries
        .findDefaultLocation(branchId)
        .orElseThrow(
            () ->
                new InvalidSaleOperationException(
                    "no-default-location",
                    "Filial sem local default ativo — crie um StockLocation com asDefault=true antes de fechar venda."));
  }

  private SaleSummary summary(Sale s) {
    List<SaleItemSummary> snapItems =
        saleItems.findBySaleIdOrderByLineNumberAsc(s.id().value()).stream()
            .map(SaleItemSummary::of)
            .toList();
    return SaleSummary.of(s, snapItems);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
