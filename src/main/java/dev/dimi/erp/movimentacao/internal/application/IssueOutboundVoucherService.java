package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockQueryPort;
import dev.dimi.erp.movimentacao.OutboundVoucherId;
import dev.dimi.erp.movimentacao.OutboundVoucherIssuedEvent;
import dev.dimi.erp.movimentacao.internal.domain.OutboundVoucher;
import dev.dimi.erp.movimentacao.internal.domain.OutboundVoucherItem;
import dev.dimi.erp.movimentacao.internal.persistence.OutboundVoucherItemRepository;
import dev.dimi.erp.movimentacao.internal.persistence.OutboundVoucherRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
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
 * Emissão de saída avulsa (RN-MOV-022). Operação atômica: persiste documento + baixa estoque por
 * item via {@code StockInventoryPort.reserve+ship} pareados (mesmo padrão de {@code
 * SaleService.close}). Step-up no controller.
 *
 * <p>NFe opcional configurável (RN-MOV-022) fica para o slice fiscal — v1 só registra a baixa
 * interna.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IssueOutboundVoucherService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "outbound_voucher";

  private final OutboundVoucherRepository vouchers;
  private final OutboundVoucherItemRepository items;
  private final DocumentNumberSequencer sequencer;
  private final StockInventoryPort inventory;
  private final StockQueryPort stockQueries;
  private final ApplicationEventPublisher events;

  @Transactional
  public OutboundVoucherSummary issue(IssueOutboundVoucherCommand cmd) {
    if (cmd.items() == null || cmd.items().isEmpty()) {
      throw new InvalidSaleOperationException(
          "empty-voucher", "Saída avulsa sem itens; informe pelo menos um packaging.");
    }
    StockLocationId location = defaultLocationFor(cmd.branchId());

    OutboundVoucher saved = persistWithRetry(cmd);

    List<OutboundVoucherItem> savedItems = new ArrayList<>();
    int line = 1;
    for (IssueOutboundVoucherCommand.Item raw : cmd.items()) {
      savedItems.add(
          items.save(
              OutboundVoucherItem.create(
                  saved.id().value(), line++, raw.packagingId().value(), raw.quantity())));
      // Reserve + ship pareados — RN-MOV-001 padrão usado em SaleService.close.
      inventory.reserve(
          new StockInventoryPort.ReserveCommand(
              raw.packagingId(),
              cmd.branchId(),
              location,
              null,
              raw.quantity(),
              "outbound-voucher",
              saved.id().value(),
              null,
              cmd.actor()));
      String idemKey =
          "voucher:" + saved.id().value() + ":" + (line - 1); // line já incrementado, ajustar
      inventory.ship(
          new StockInventoryPort.ShipCommand(
              raw.packagingId(),
              cmd.branchId(),
              location,
              null,
              raw.quantity(),
              "outbound-voucher",
              saved.id().value(),
              idemKey,
              cmd.actor()));
    }

    events.publishEvent(
        new OutboundVoucherIssuedEvent(
            saved.id(),
            currentTenantId(),
            cmd.branchId(),
            cmd.reason(),
            cmd.actor(),
            Instant.now()));
    log.info(
        "outbound-voucher issued id={} branch={} reason={} number={} actor={}",
        saved.id().value(),
        cmd.branchId().value(),
        cmd.reason(),
        saved.getNumber(),
        cmd.actor().value());
    return OutboundVoucherSummary.of(
        saved, savedItems.stream().map(OutboundVoucherItemSummary::of).toList());
  }

  @Transactional(readOnly = true)
  public OutboundVoucherSummary findById(OutboundVoucherId voucherId) {
    OutboundVoucher v =
        vouchers
            .findById(voucherId.value())
            .orElseThrow(() -> new OutboundVoucherNotFoundException(voucherId));
    return summary(v);
  }

  private OutboundVoucherSummary summary(OutboundVoucher v) {
    List<OutboundVoucherItemSummary> snap =
        items.findByVoucherIdOrderByLineNumberAsc(v.id().value()).stream()
            .map(OutboundVoucherItemSummary::of)
            .toList();
    return OutboundVoucherSummary.of(v, snap);
  }

  private OutboundVoucher persistWithRetry(IssueOutboundVoucherCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(TABLE, cmd.branchId().value());
        OutboundVoucher v =
            OutboundVoucher.issue(
                number,
                cmd.branchId().value(),
                cmd.reason(),
                cmd.recipientPersonId() == null ? null : cmd.recipientPersonId().value(),
                cmd.notes());
        return vouchers.saveAndFlush(v);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("voucher number collision, retrying attempt={}", attempts);
      }
    }
  }

  private StockLocationId defaultLocationFor(BranchId branchId) {
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
