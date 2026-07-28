package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockQueryPort;
import dev.dimi.erp.movimentacao.ReturnStatus;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleReturnClosedEvent;
import dev.dimi.erp.movimentacao.SaleReturnId;
import dev.dimi.erp.movimentacao.SaleStatus;
import dev.dimi.erp.movimentacao.internal.domain.Sale;
import dev.dimi.erp.movimentacao.internal.domain.SaleItem;
import dev.dimi.erp.movimentacao.internal.domain.SaleReturn;
import dev.dimi.erp.movimentacao.internal.domain.SaleReturnItem;
import dev.dimi.erp.movimentacao.internal.persistence.SaleItemRepository;
import dev.dimi.erp.movimentacao.internal.persistence.SaleRepository;
import dev.dimi.erp.movimentacao.internal.persistence.SaleReturnItemRepository;
import dev.dimi.erp.movimentacao.internal.persistence.SaleReturnRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sobre {@code sale_return}:
 *
 * <ul>
 *   <li>{@link #open} cria devolução referenciando venda CLOSED (RN-MOV-013). Cada item carrega 3
 *       quantidades; soma ≤ {@code sale_item.quantity} (RN-MOV-015).
 *   <li>{@link #close} devolve estoque das quantidades returned via {@code
 *       StockInventoryPort.receive} (sem custo — preserva WAC do snapshot, RN-EST-001) e emite
 *       {@link SaleReturnClosedEvent} para financeiro/fiscal consumirem.
 * </ul>
 *
 * <p>v1 (slice 5.3): NFe de devolução (RN-MOV-017) e estorno de MVF (RN-MOV-018) ficam para slices
 * fiscal/financeiro.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessReturnService {

  private final SaleReturnRepository returns;
  private final SaleReturnItemRepository returnItems;
  private final SaleRepository sales;
  private final SaleItemRepository saleItems;
  private final StockInventoryPort inventory;
  private final StockQueryPort stockQueries;
  private final ApplicationEventPublisher events;

  @Transactional
  public SaleReturnSummary open(OpenSaleReturnCommand cmd) {
    Sale sale =
        sales
            .findById(cmd.saleId().value())
            .orElseThrow(() -> new SaleNotFoundException(cmd.saleId()));
    if (sale.getStatus() != SaleStatus.CLOSED) {
      throw new InvalidSaleStateException(sale.getStatus(), "open-return");
    }
    if (cmd.items() == null || cmd.items().isEmpty()) {
      throw new InvalidSaleOperationException(
          "empty-return", "Devolução sem itens; informe pelo menos um sale_item.");
    }

    Map<java.util.UUID, SaleItem> saleItemsById = new HashMap<>();
    for (SaleItem si : saleItems.findBySaleIdOrderByLineNumberAsc(sale.id().value())) {
      saleItemsById.put(si.id().value(), si);
    }

    SaleReturn r =
        returns.save(
            SaleReturn.open(sale.id().value(), sale.getBranchId(), cmd.reason(), cmd.notes()));

    BigDecimal total = BigDecimal.ZERO;
    List<SaleReturnItem> savedItems = new ArrayList<>();
    for (OpenSaleReturnCommand.Item raw : cmd.items()) {
      SaleItem source = saleItemsById.get(raw.saleItemId().value());
      if (source == null) {
        throw new InvalidSaleOperationException(
            "sale-item-mismatch",
            "saleItemId não pertence à venda informada: " + raw.saleItemId().value());
      }
      BigDecimal qReturned =
          raw.quantityReturned() == null ? BigDecimal.ZERO : raw.quantityReturned();
      BigDecimal qLost = raw.quantityLost() == null ? BigDecimal.ZERO : raw.quantityLost();
      BigDecimal qUsed = raw.quantityUsed() == null ? BigDecimal.ZERO : raw.quantityUsed();
      BigDecimal sum = qReturned.add(qLost).add(qUsed);
      if (sum.signum() == 0) {
        throw new InvalidSaleOperationException(
            "zero-quantities",
            "Soma das quantidades (returned+lost+used) precisa ser > 0 para sale_item: "
                + raw.saleItemId().value());
      }
      if (sum.compareTo(source.getQuantity()) > 0) {
        throw new InvalidSaleOperationException(
            "exceeds-sale-quantity",
            "Soma das quantidades excede sale_item.quantity para sale_item: "
                + raw.saleItemId().value());
      }
      // RN-MOV-016: refund = (returned + lost) × unit_price. used não gera refund.
      BigDecimal refund =
          qReturned
              .add(qLost)
              .multiply(source.getUnitPrice())
              .setScale(2, java.math.RoundingMode.HALF_UP);
      savedItems.add(
          returnItems.save(
              SaleReturnItem.create(
                  r.id().value(), source.id().value(), qReturned, qLost, qUsed, refund)));
      total = total.add(refund);
    }
    r.updateTotal(total);
    log.info(
        "sale-return opened id={} saleId={} total={} actor={}",
        r.id().value(),
        sale.id().value(),
        total,
        cmd.actor().value());
    return SaleReturnSummary.of(r, savedItems.stream().map(SaleReturnItemSummary::of).toList());
  }

  @Transactional
  public SaleReturnSummary close(SaleReturnId returnId, dev.dimi.erp.identidade.UserId actor) {
    SaleReturn r =
        returns
            .findById(returnId.value())
            .orElseThrow(() -> new SaleReturnNotFoundException(returnId));
    if (r.getStatus() != ReturnStatus.OPEN) {
      throw new InvalidSaleStateException(SaleStatus.CLOSED, "close-return");
    }
    List<SaleReturnItem> items = returnItems.findByReturnId(r.id().value());
    if (items.isEmpty()) {
      throw new InvalidSaleOperationException(
          "empty-return", "Devolução sem itens; impossível fechar.");
    }

    StockLocationId location = defaultLocationFor(r.branchIdTyped());

    // Devolve estoque das quantidades returned. lost/used não retornam ao estoque.
    // receive() com unitCost null preserva WAC (RN-EST-009 lógica: estorno não recalcula custo).
    for (SaleReturnItem item : items) {
      if (item.getQuantityReturned().signum() <= 0) {
        continue;
      }
      // Busca packagingId via sale_item (return só carrega referência ao sale_item).
      SaleItem source =
          saleItems
              .findById(item.getSaleItemId())
              .orElseThrow(
                  () ->
                      new InvalidSaleOperationException(
                          "sale-item-missing",
                          "sale_item original sumiu — return em estado inconsistente."));
      String idemKey = "return:" + r.id().value() + ":" + item.id().value();
      inventory.receive(
          new StockInventoryPort.ReceiveCommand(
              source.packagingIdTyped(),
              r.branchIdTyped(),
              location,
              null,
              item.getQuantityReturned(),
              null, // unitCost null preserva WAC do snapshot
              "sale-return",
              r.id().value(),
              idemKey,
              actor));
    }

    r.close();
    events.publishEvent(
        new SaleReturnClosedEvent(
            r.id(),
            r.saleIdTyped(),
            currentTenantId(),
            r.branchIdTyped(),
            r.getTotal(),
            actor,
            Instant.now()));
    log.info(
        "sale-return closed id={} saleId={} total={} actor={}",
        r.id().value(),
        r.getSaleId(),
        r.getTotal(),
        actor.value());
    return SaleReturnSummary.of(r, items.stream().map(SaleReturnItemSummary::of).toList());
  }

  @Transactional(readOnly = true)
  public SaleReturnSummary findById(SaleReturnId returnId) {
    SaleReturn r =
        returns
            .findById(returnId.value())
            .orElseThrow(() -> new SaleReturnNotFoundException(returnId));
    return summary(r);
  }

  @Transactional(readOnly = true)
  public List<SaleReturnSummary> listBySale(SaleId saleId) {
    return returns.findBySaleId(saleId.value()).stream().map(this::summary).toList();
  }

  private SaleReturnSummary summary(SaleReturn r) {
    List<SaleReturnItemSummary> snap =
        returnItems.findByReturnId(r.id().value()).stream().map(SaleReturnItemSummary::of).toList();
    return SaleReturnSummary.of(r, snap);
  }

  private StockLocationId defaultLocationFor(dev.dimi.erp.cadastros.BranchId branchId) {
    return stockQueries
        .findDefaultLocation(branchId)
        .orElseThrow(
            () ->
                new InvalidSaleOperationException(
                    "no-default-location",
                    "Filial sem local default ativo — crie um StockLocation com asDefault=true antes."));
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
