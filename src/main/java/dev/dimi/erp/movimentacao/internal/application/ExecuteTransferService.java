package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockQueryPort;
import dev.dimi.erp.movimentacao.StockTransferClosedEvent;
import dev.dimi.erp.movimentacao.StockTransferId;
import dev.dimi.erp.movimentacao.TransferStatus;
import dev.dimi.erp.movimentacao.internal.domain.StockTransfer;
import dev.dimi.erp.movimentacao.internal.domain.StockTransferItem;
import dev.dimi.erp.movimentacao.internal.persistence.StockTransferItemRepository;
import dev.dimi.erp.movimentacao.internal.persistence.StockTransferRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações de transferência. {@link #open} cria documento em OPEN com itens; {@link #close}
 * dispara o par atômico via {@code StockInventoryPort.transfer} (RN-EST-008) — falha em qualquer
 * etapa rolla back tudo.
 *
 * <p>Transferência entre CNPJs distintos vai emitir NFe (RN-MOV-020) no slice fiscal; v1 trata
 * mesmo-CNPJ apenas (RN-MOV-019).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExecuteTransferService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "stock_transfer";

  private final StockTransferRepository transfers;
  private final StockTransferItemRepository items;
  private final DocumentNumberSequencer sequencer;
  private final StockInventoryPort inventory;
  private final StockQueryPort stockQueries;
  private final ApplicationEventPublisher events;

  @Transactional
  public StockTransferSummary open(OpenStockTransferCommand cmd) {
    if (cmd.items() == null || cmd.items().isEmpty()) {
      throw new InvalidSaleOperationException(
          "empty-transfer", "Transferência sem itens; informe pelo menos um packaging.");
    }
    if (cmd.originBranchId().equals(cmd.destBranchId())
        && cmd.originLocationId() != null
        && cmd.originLocationId().equals(cmd.destLocationId())) {
      throw new InvalidSaleOperationException(
          "same-origin-dest",
          "Origem e destino da transferência precisam diferir (filial ou local).");
    }

    StockTransfer saved = persistWithRetry(cmd);
    List<StockTransferItem> savedItems = new ArrayList<>();
    int line = 1;
    for (OpenStockTransferCommand.Item raw : cmd.items()) {
      savedItems.add(
          items.save(
              StockTransferItem.create(
                  saved.id().value(), line++, raw.packagingId().value(), raw.quantity())));
    }
    log.info(
        "stock-transfer opened id={} origin={} dest={} number={} actor={}",
        saved.id().value(),
        cmd.originBranchId().value(),
        cmd.destBranchId().value(),
        saved.getNumber(),
        cmd.actor().value());
    return StockTransferSummary.of(
        saved, savedItems.stream().map(StockTransferItemSummary::of).toList());
  }

  @Transactional
  public StockTransferSummary close(
      StockTransferId transferId, dev.dimi.erp.identidade.UserId actor) {
    StockTransfer t =
        transfers
            .findById(transferId.value())
            .orElseThrow(() -> new StockTransferNotFoundException(transferId));
    if (t.getStatus() != TransferStatus.OPEN) {
      throw new InvalidSaleStateException(
          dev.dimi.erp.movimentacao.SaleStatus.CLOSED, "close-transfer");
    }
    List<StockTransferItem> currentItems =
        items.findByTransferIdOrderByLineNumberAsc(t.id().value());
    if (currentItems.isEmpty()) {
      throw new InvalidSaleOperationException(
          "empty-transfer", "Transferência sem itens; impossível fechar.");
    }

    StockLocationId originLoc =
        t.originLocationIdTyped() == null
            ? defaultLocationFor(t.originBranchIdTyped())
            : t.originLocationIdTyped();
    StockLocationId destLoc =
        t.destLocationIdTyped() == null
            ? defaultLocationFor(t.destBranchIdTyped())
            : t.destLocationIdTyped();

    // RN-EST-008: par atômico TRANSFER_OUT + TRANSFER_IN por item. transferId compartilhado entre
    // os itens é gerado por linha (StockInventoryPort.transfer assina transferId, então usamos
    // UUID novo por linha para idempotência fina). Falha em qualquer item rolla back toda a
    // transação @Transactional.
    for (StockTransferItem item : currentItems) {
      UUID lineTransferId = UUID.randomUUID();
      inventory.transfer(
          new StockInventoryPort.TransferCommand(
              item.packagingIdTyped(),
              t.originBranchIdTyped(),
              originLoc,
              t.destBranchIdTyped(),
              destLoc,
              null,
              item.getQuantity(),
              lineTransferId,
              "transfer:" + t.id().value() + ":" + item.getLineNumber(),
              actor));
    }

    t.close();
    events.publishEvent(
        new StockTransferClosedEvent(
            t.id(),
            currentTenantId(),
            t.originBranchIdTyped(),
            t.destBranchIdTyped(),
            actor,
            Instant.now()));
    log.info(
        "stock-transfer closed id={} number={} actor={}",
        t.id().value(),
        t.getNumber(),
        actor.value());
    return StockTransferSummary.of(
        t, currentItems.stream().map(StockTransferItemSummary::of).toList());
  }

  @Transactional(readOnly = true)
  public StockTransferSummary findById(StockTransferId transferId) {
    StockTransfer t =
        transfers
            .findById(transferId.value())
            .orElseThrow(() -> new StockTransferNotFoundException(transferId));
    return summary(t);
  }

  private StockTransferSummary summary(StockTransfer t) {
    List<StockTransferItemSummary> snap =
        items.findByTransferIdOrderByLineNumberAsc(t.id().value()).stream()
            .map(StockTransferItemSummary::of)
            .toList();
    return StockTransferSummary.of(t, snap);
  }

  private StockTransfer persistWithRetry(OpenStockTransferCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextForOriginBranch(TABLE, cmd.originBranchId().value());
        StockTransfer t =
            StockTransfer.open(
                number,
                cmd.originBranchId().value(),
                cmd.destBranchId().value(),
                cmd.originLocationId() == null ? null : cmd.originLocationId().value(),
                cmd.destLocationId() == null ? null : cmd.destLocationId().value(),
                cmd.notes());
        return transfers.saveAndFlush(t);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("transfer number collision, retrying attempt={}", attempts);
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
