package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.movimentacao.InventoryAdjustmentId;
import dev.dimi.erp.movimentacao.InventoryAdjustmentRecordedEvent;
import dev.dimi.erp.movimentacao.internal.domain.InventoryAdjustment;
import dev.dimi.erp.movimentacao.internal.domain.InventoryAdjustmentItem;
import dev.dimi.erp.movimentacao.internal.persistence.InventoryAdjustmentItemRepository;
import dev.dimi.erp.movimentacao.internal.persistence.InventoryAdjustmentRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registro de ajuste de inventário batch por conferência (RN-MOV-024). Cada linha dispara {@code
 * StockInventoryPort.adjust} com {@code delta = counted - expected}. Step-up no controller
 * (RN-MOV-025).
 *
 * <p>{@code delta == 0} é ignorado — não gera movimento (nem ruído no ledger).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RecordInventoryAdjustmentService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "stock_inventory_adjustment";

  private final InventoryAdjustmentRepository adjustments;
  private final InventoryAdjustmentItemRepository items;
  private final DocumentNumberSequencer sequencer;
  private final StockInventoryPort inventory;
  private final ApplicationEventPublisher events;

  @Transactional
  public InventoryAdjustmentSummary record(RecordInventoryAdjustmentCommand cmd) {
    if (cmd.items() == null || cmd.items().isEmpty()) {
      throw new InvalidSaleOperationException(
          "empty-adjustment", "Ajuste sem itens; informe pelo menos uma linha de conferência.");
    }
    if (cmd.reason() == null || cmd.reason().isBlank()) {
      throw new InvalidSaleOperationException(
          "adjustment-reason-required", "Motivo do ajuste é obrigatório.");
    }

    InventoryAdjustment saved = persistWithRetry(cmd);

    List<InventoryAdjustmentItem> savedItems = new ArrayList<>();
    for (RecordInventoryAdjustmentCommand.Item raw : cmd.items()) {
      savedItems.add(
          items.save(
              InventoryAdjustmentItem.create(
                  saved.id().value(),
                  raw.packagingId().value(),
                  raw.locationId().value(),
                  raw.expectedQty(),
                  raw.countedQty())));
      BigDecimal delta = raw.countedQty().subtract(raw.expectedQty());
      if (delta.signum() == 0) {
        continue; // sem movimento — não suja o ledger
      }
      inventory.adjust(
          new StockInventoryPort.AdjustCommand(
              raw.packagingId(),
              cmd.branchId(),
              raw.locationId(),
              null,
              delta,
              "inventory-adjustment:" + saved.id().value() + " — " + cmd.reason(),
              cmd.actor()));
    }

    events.publishEvent(
        new InventoryAdjustmentRecordedEvent(
            saved.id(),
            currentTenantId(),
            cmd.branchId(),
            savedItems.size(),
            cmd.actor(),
            Instant.now()));
    log.info(
        "inventory-adjustment recorded id={} branch={} lines={} actor={}",
        saved.id().value(),
        cmd.branchId().value(),
        savedItems.size(),
        cmd.actor().value());
    return InventoryAdjustmentSummary.of(
        saved, savedItems.stream().map(InventoryAdjustmentItemSummary::of).toList());
  }

  @Transactional(readOnly = true)
  public InventoryAdjustmentSummary findById(InventoryAdjustmentId adjustmentId) {
    InventoryAdjustment a =
        adjustments
            .findById(adjustmentId.value())
            .orElseThrow(() -> new InventoryAdjustmentNotFoundException(adjustmentId));
    return summary(a);
  }

  private InventoryAdjustmentSummary summary(InventoryAdjustment a) {
    List<InventoryAdjustmentItemSummary> snap =
        items.findByAdjustmentId(a.id().value()).stream()
            .map(InventoryAdjustmentItemSummary::of)
            .toList();
    return InventoryAdjustmentSummary.of(a, snap);
  }

  private InventoryAdjustment persistWithRetry(RecordInventoryAdjustmentCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(TABLE, cmd.branchId().value());
        InventoryAdjustment a =
            InventoryAdjustment.record(
                number, cmd.branchId().value(), cmd.reason(), cmd.notes(), cmd.actor().value());
        return adjustments.saveAndFlush(a);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("adjustment number collision, retrying attempt={}", attempts);
      }
    }
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
