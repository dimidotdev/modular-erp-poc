package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PriceTableId;
import dev.dimi.erp.produto.PriceTableUpdatedEvent;
import dev.dimi.erp.produto.internal.domain.PriceTable;
import dev.dimi.erp.produto.internal.domain.PriceTableItem;
import dev.dimi.erp.produto.internal.persistence.PackagingRepository;
import dev.dimi.erp.produto.internal.persistence.PriceTableItemRepository;
import dev.dimi.erp.produto.internal.persistence.PriceTableRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD de {@link PriceTable} e batch upsert de {@link PriceTableItem}. Emite {@link
 * PriceTableUpdatedEvent} a cada criação/atualização significativa para consumidores invalidarem
 * cache (movimentação, dashboards).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManagePriceTableService {

  private final PriceTableRepository tables;
  private final PriceTableItemRepository items;
  private final PackagingRepository packagings;
  private final ApplicationEventPublisher events;

  // ---------------------------------------------------------------------------
  // PriceTable CRUD
  // ---------------------------------------------------------------------------

  @Transactional
  public PriceTableSummary register(RegisterPriceTableCommand cmd) {
    PriceTable saved =
        tables.save(
            PriceTable.create(
                cmd.name(),
                cmd.branchId() == null ? null : cmd.branchId().value(),
                cmd.validFrom(),
                cmd.validUntil()));
    publishUpdated(saved, cmd.branchId(), 0, cmd.actor());
    log.info(
        "price-table registered id={} branch={} actor={}",
        saved.id().value(),
        saved.getBranchId(),
        cmd.actor().value());
    return PriceTableSummary.of(saved);
  }

  @Transactional
  public PriceTableSummary update(UpdatePriceTableCommand cmd) {
    PriceTable t = load(cmd.priceTableId());
    t.update(cmd.name(), cmd.validFrom(), cmd.validUntil());
    publishUpdated(
        t, t.getBranchId() == null ? null : new BranchId(t.getBranchId()), 0, cmd.actor());
    return PriceTableSummary.of(t);
  }

  @Transactional
  public PriceTableSummary deactivate(PriceTableId priceTableId) {
    PriceTable t = load(priceTableId);
    t.deactivate();
    return PriceTableSummary.of(t);
  }

  @Transactional
  public PriceTableSummary reactivate(PriceTableId priceTableId) {
    PriceTable t = load(priceTableId);
    t.reactivate();
    return PriceTableSummary.of(t);
  }

  @Transactional(readOnly = true)
  public PriceTableSummary findById(PriceTableId priceTableId) {
    return PriceTableSummary.of(load(priceTableId));
  }

  @Transactional(readOnly = true)
  public List<PriceTableSummary> list() {
    return tables.findAll().stream().map(PriceTableSummary::of).toList();
  }

  @Transactional(readOnly = true)
  public List<PriceTableSummary> listByBranch(BranchId branchId) {
    return tables.findByBranchId(branchId.value()).stream().map(PriceTableSummary::of).toList();
  }

  // ---------------------------------------------------------------------------
  // PriceTableItem batch upsert + listing
  // ---------------------------------------------------------------------------

  @Transactional
  public List<PriceTableItemSummary> upsertItems(UpsertPriceItemsCommand cmd) {
    PriceTable t = load(cmd.priceTableId());
    Objects.requireNonNull(cmd.items(), "items");

    cmd.items()
        .forEach(
            i -> {
              if (!packagings.existsById(i.packagingId().value())) {
                throw new PackagingNotFoundException(i.packagingId());
              }
            });

    int touched;
    if (cmd.replaceAll()) {
      items.deleteByPriceTableId(t.id().value());
      List<PriceTableItem> created =
          cmd.items().stream()
              .map(
                  i ->
                      PriceTableItem.create(
                          t.id().value(), i.packagingId().value(), i.salePrice(), i.discountMax()))
              .toList();
      items.saveAll(created);
      touched = created.size();
    } else {
      Map<UUID, PriceTableItem> existing = new HashMap<>();
      for (PriceTableItem item : items.findByPriceTableId(t.id().value())) {
        existing.put(item.getPackagingId(), item);
      }
      touched = 0;
      for (UpsertPriceItemsCommand.Item incoming : cmd.items()) {
        PriceTableItem found = existing.get(incoming.packagingId().value());
        if (found == null) {
          items.save(
              PriceTableItem.create(
                  t.id().value(),
                  incoming.packagingId().value(),
                  incoming.salePrice(),
                  incoming.discountMax()));
        } else {
          found.changePrice(incoming.salePrice(), incoming.discountMax());
        }
        touched++;
      }
    }

    publishUpdated(
        t, t.getBranchId() == null ? null : new BranchId(t.getBranchId()), touched, cmd.actor());
    log.info(
        "price-table items upserted table={} touched={} replaceAll={} actor={}",
        t.id().value(),
        touched,
        cmd.replaceAll(),
        cmd.actor().value());
    return items.findByPriceTableId(t.id().value()).stream()
        .map(PriceTableItemSummary::of)
        .toList();
  }

  @Transactional(readOnly = true)
  public List<PriceTableItemSummary> listItems(PriceTableId priceTableId) {
    load(priceTableId); // valida existência
    return items.findByPriceTableId(priceTableId.value()).stream()
        .map(PriceTableItemSummary::of)
        .toList();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private PriceTable load(PriceTableId priceTableId) {
    return tables
        .findById(priceTableId.value())
        .orElseThrow(() -> new PriceTableNotFoundException(priceTableId));
  }

  private void publishUpdated(
      PriceTable t, BranchId branchId, int itemsTouched, dev.dimi.erp.identidade.UserId actor) {
    events.publishEvent(
        new PriceTableUpdatedEvent(
            t.id(), currentTenantId(), branchId, itemsTouched, actor, Instant.now()));
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }

  /** Helper para o adapter consultar tabelas vigentes (mesmo módulo, sem expor port). */
  @Transactional(readOnly = true)
  List<PriceTable> findActiveOn(BranchId branchId, LocalDate when) {
    return tables.findActiveOn(branchId.value(), when);
  }
}
