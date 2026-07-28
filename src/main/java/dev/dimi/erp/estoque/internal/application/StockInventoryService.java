package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.estoque.Direction;
import dev.dimi.erp.estoque.MovementId;
import dev.dimi.erp.estoque.StockAdjustmentRecordedEvent;
import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.estoque.StockMovementRecordedEvent;
import dev.dimi.erp.estoque.StockReservationReleasedEvent;
import dev.dimi.erp.estoque.StockReservedEvent;
import dev.dimi.erp.estoque.internal.domain.StockMovement;
import dev.dimi.erp.estoque.internal.domain.StockSnapshot;
import dev.dimi.erp.estoque.internal.persistence.StockMovementRepository;
import dev.dimi.erp.estoque.internal.persistence.StockSnapshotRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orquestrador do ledger de estoque. Implementa as 7 operações do {@link StockInventoryPort} com:
 *
 * <ul>
 *   <li><b>Lock pessimista</b> (RN-EST-004) via {@code findByKeyForUpdate} antes de qualquer
 *       mutação — duas transações concorrentes na mesma tupla bloqueiam até a primeira commitar.
 *   <li><b>Idempotência</b> (RN-EST-005) via {@code findByIdempotencyKey} antes de gravar — chamada
 *       duplicada com mesma key retorna o movimento original sem efeito colateral.
 *   <li><b>WAC</b> (RN-EST-001) recalculado em {@code receive} dentro de {@link
 *       StockSnapshot#applyInbound}; demais operações preservam o custo.
 *   <li><b>Snapshot auto-criado</b> na primeira operação contra uma tupla nova ({@code
 *       packaging,branch,location,batch}).
 * </ul>
 *
 * <p>TRANSFER é o caso atômico mais delicado: dois movimentos (OUT/IN) + duas mutações de snapshot
 * + mesmo {@code transferId} como {@code origin_id}. Tudo dentro da mesma transação — qualquer
 * falha (ex: saldo insuficiente na origem) reverte tudo.
 *
 * <p>REVERSAL (RN-EST-009) cria movimento com sentido inverso ao original e ajusta saldo
 * proporcionalmente; NÃO recalcula WAC retroativamente, por decisão explícita de escopo.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class StockInventoryService implements StockInventoryPort {

  private final StockSnapshotRepository snapshots;
  private final StockMovementRepository movements;
  private final ApplicationEventPublisher events;

  // ---------------------------------------------------------------------------
  // Reserve / Release / Ship — não-cost-impacting (não recalcula WAC)
  // ---------------------------------------------------------------------------

  @Override
  @Transactional
  public void reserve(ReserveCommand cmd) {
    StockSnapshot snap =
        lockOrCreate(
            cmd.packagingId().value(),
            cmd.branchId().value(),
            cmd.locationId().value(),
            cmd.batchId() == null ? null : cmd.batchId().value());

    BigDecimal available = snap.quantityAvailable();
    if (cmd.quantity().compareTo(available) > 0) {
      throw new StockInsufficientException(
          cmd.packagingId(), cmd.branchId(), cmd.quantity(), available);
    }
    snap.reserve(cmd.quantity());
    events.publishEvent(
        new StockReservedEvent(
            currentTenantId(),
            cmd.packagingId(),
            cmd.branchId(),
            cmd.quantity(),
            cmd.originType(),
            cmd.originId(),
            Instant.now()));
    log.debug(
        "stock reserved packaging={} branch={} qty={}",
        cmd.packagingId().value(),
        cmd.branchId().value(),
        cmd.quantity());
  }

  @Override
  @Transactional
  public void releaseReservation(ReleaseReservationCommand cmd) {
    StockSnapshot snap =
        lockExisting(
            cmd.packagingId().value(),
            cmd.branchId().value(),
            cmd.locationId().value(),
            cmd.batchId() == null ? null : cmd.batchId().value());
    snap.releaseReservation(cmd.quantity());
    events.publishEvent(
        new StockReservationReleasedEvent(
            currentTenantId(),
            cmd.packagingId(),
            cmd.branchId(),
            cmd.quantity(),
            cmd.originType(),
            cmd.originId(),
            Instant.now()));
  }

  @Override
  @Transactional
  public MovementId ship(ShipCommand cmd) {
    Optional<StockMovement> existing = findByIdempotency(cmd.idempotencyKey());
    if (existing.isPresent()) {
      return existing.get().id();
    }
    StockSnapshot snap =
        lockExisting(
            cmd.packagingId().value(),
            cmd.branchId().value(),
            cmd.locationId().value(),
            cmd.batchId() == null ? null : cmd.batchId().value());
    snap.shipReservation(cmd.quantity());
    StockMovement m =
        recordMovement(
            Direction.OUTBOUND,
            "SHIP",
            cmd.packagingId().value(),
            cmd.branchId().value(),
            cmd.locationId().value(),
            cmd.batchId() == null ? null : cmd.batchId().value(),
            cmd.quantity(),
            null,
            cmd.originType(),
            cmd.originId(),
            cmd.idempotencyKey(),
            cmd.actor().value());
    publishMovementRecorded(m, cmd.actor().value());
    return m.id();
  }

  // ---------------------------------------------------------------------------
  // Receive — INBOUND com custo, recalcula WAC
  // ---------------------------------------------------------------------------

  @Override
  @Transactional
  public MovementId receive(ReceiveCommand cmd) {
    Optional<StockMovement> existing = findByIdempotency(cmd.idempotencyKey());
    if (existing.isPresent()) {
      return existing.get().id();
    }
    StockSnapshot snap =
        lockOrCreate(
            cmd.packagingId().value(),
            cmd.branchId().value(),
            cmd.locationId().value(),
            cmd.batchId() == null ? null : cmd.batchId().value());
    snap.applyInbound(cmd.quantity(), cmd.unitCost());
    StockMovement m =
        recordMovement(
            Direction.INBOUND,
            "RECEIVE",
            cmd.packagingId().value(),
            cmd.branchId().value(),
            cmd.locationId().value(),
            cmd.batchId() == null ? null : cmd.batchId().value(),
            cmd.quantity(),
            cmd.unitCost(),
            cmd.originType(),
            cmd.originId(),
            cmd.idempotencyKey(),
            cmd.actor().value());
    publishMovementRecorded(m, cmd.actor().value());
    return m.id();
  }

  // ---------------------------------------------------------------------------
  // Transfer — par atômico TRANSFER_OUT + TRANSFER_IN
  // ---------------------------------------------------------------------------

  @Override
  @Transactional
  public TransferResult transfer(TransferCommand cmd) {
    if (cmd.sourceBranchId().equals(cmd.destBranchId())
        && cmd.sourceLocationId().equals(cmd.destLocationId())) {
      throw new InvalidStockOperationException(
          "transfer-same-source-dest", "Origem e destino da transferência devem diferir.");
    }
    // Lock ordering por UUID para evitar deadlocks em transferências cruzadas.
    UUID sourceLocUuid = cmd.sourceLocationId().value();
    UUID destLocUuid = cmd.destLocationId().value();
    UUID batchUuid = cmd.batchId() == null ? null : cmd.batchId().value();
    boolean sourceFirst = sourceLocUuid.compareTo(destLocUuid) < 0;

    StockSnapshot source;
    StockSnapshot dest;
    if (sourceFirst) {
      source =
          lockExisting(
              cmd.packagingId().value(), cmd.sourceBranchId().value(), sourceLocUuid, batchUuid);
      dest =
          lockOrCreate(
              cmd.packagingId().value(), cmd.destBranchId().value(), destLocUuid, batchUuid);
    } else {
      dest =
          lockOrCreate(
              cmd.packagingId().value(), cmd.destBranchId().value(), destLocUuid, batchUuid);
      source =
          lockExisting(
              cmd.packagingId().value(), cmd.sourceBranchId().value(), sourceLocUuid, batchUuid);
    }

    // Preserva WAC do source no destino — transferência não cria valor.
    BigDecimal unitCost = source.getWeightedAvgCost();
    source.applyOutbound(cmd.quantity());
    dest.applyInbound(cmd.quantity(), unitCost);

    String reason = cmd.reason() == null ? "TRANSFER" : cmd.reason();
    String idemOut = "transfer:" + cmd.transferId() + ":out";
    String idemIn = "transfer:" + cmd.transferId() + ":in";

    StockMovement out =
        recordMovement(
            Direction.TRANSFER_OUT,
            reason,
            cmd.packagingId().value(),
            cmd.sourceBranchId().value(),
            sourceLocUuid,
            batchUuid,
            cmd.quantity(),
            unitCost,
            "transfer",
            cmd.transferId(),
            idemOut,
            cmd.actor().value());
    StockMovement in =
        recordMovement(
            Direction.TRANSFER_IN,
            reason,
            cmd.packagingId().value(),
            cmd.destBranchId().value(),
            destLocUuid,
            batchUuid,
            cmd.quantity(),
            unitCost,
            "transfer",
            cmd.transferId(),
            idemIn,
            cmd.actor().value());
    publishMovementRecorded(out, cmd.actor().value());
    publishMovementRecorded(in, cmd.actor().value());
    log.info(
        "stock transferred packaging={} qty={} from={} to={} transferId={}",
        cmd.packagingId().value(),
        cmd.quantity(),
        cmd.sourceBranchId().value(),
        cmd.destBranchId().value(),
        cmd.transferId());
    return new TransferResult(out.id(), in.id());
  }

  // ---------------------------------------------------------------------------
  // Adjust — manual com motivo obrigatório (step-up no caller)
  // ---------------------------------------------------------------------------

  @Override
  @Transactional
  public MovementId adjust(AdjustCommand cmd) {
    if (cmd.delta() == null || cmd.delta().signum() == 0) {
      throw new InvalidStockOperationException(
          "adjust-zero-delta", "Ajuste exige delta diferente de zero.");
    }
    if (cmd.reason() == null || cmd.reason().isBlank()) {
      throw new InvalidStockOperationException(
          "adjust-reason-required", "Motivo do ajuste é obrigatório.");
    }
    StockSnapshot snap =
        lockOrCreate(
            cmd.packagingId().value(),
            cmd.branchId().value(),
            cmd.locationId().value(),
            cmd.batchId() == null ? null : cmd.batchId().value());
    BigDecimal absDelta = cmd.delta().abs();
    if (cmd.delta().signum() > 0) {
      snap.applyInbound(absDelta, null); // ajuste positivo não altera WAC (sem custo informado)
    } else {
      snap.applyOutbound(absDelta);
    }
    StockMovement m =
        recordMovement(
            Direction.ADJUSTMENT,
            cmd.reason(),
            cmd.packagingId().value(),
            cmd.branchId().value(),
            cmd.locationId().value(),
            cmd.batchId() == null ? null : cmd.batchId().value(),
            absDelta,
            null,
            "adjustment",
            null,
            null,
            cmd.actor().value());
    events.publishEvent(
        new StockAdjustmentRecordedEvent(
            m.id(), currentTenantId(), cmd.delta(), cmd.reason(), cmd.actor(), Instant.now()));
    publishMovementRecorded(m, cmd.actor().value());
    log.info(
        "stock adjusted packaging={} branch={} delta={} reason='{}' actor={}",
        cmd.packagingId().value(),
        cmd.branchId().value(),
        cmd.delta(),
        cmd.reason(),
        cmd.actor().value());
    return m.id();
  }

  // ---------------------------------------------------------------------------
  // Reverse — estorno de movimento prévio
  // ---------------------------------------------------------------------------

  @Override
  @Transactional
  public MovementId reverse(ReverseCommand cmd) {
    StockMovement original =
        movements
            .findById(cmd.movementId().value())
            .orElseThrow(() -> new MovementNotFoundException(cmd.movementId()));
    if (original.getDirection() == Direction.REVERSAL) {
      throw new InvalidStockOperationException(
          "reverse-of-reversal", "Não é possível estornar um REVERSAL.");
    }
    if (original.getReason() != null && original.getReason().startsWith("REVERSAL_OF:")) {
      throw new InvalidStockOperationException(
          "reverse-already-reversed", "Movimento já estornado anteriormente.");
    }

    StockSnapshot snap =
        lockOrCreate(
            original.getPackagingId(),
            original.getBranchId(),
            original.getLocationId(),
            original.getBatchId());

    // Sentido inverso ao original. REVERSAL não recalcula WAC (RN-EST-009).
    // ADJUSTMENT NÃO é estornável: `stock_movement.quantity` é sempre positiva e o sinal do
    // delta original não é persistido (perderia o significado de "ajuste manual" se tivesse
    // direction separado). Correção de ADJUSTMENT é via OUTRO ADJUSTMENT com delta inverso.
    switch (original.getDirection()) {
      case INBOUND, TRANSFER_IN -> snap.applyOutbound(original.getQuantity());
      case OUTBOUND, TRANSFER_OUT -> snap.applyInbound(original.getQuantity(), null);
      case ADJUSTMENT ->
          throw new InvalidStockOperationException(
              "reverse-of-adjustment",
              "Estorno de ADJUSTMENT não é suportado — emita um novo ADJUSTMENT com delta inverso.");
      default ->
          throw new InvalidStockOperationException(
              "reverse-unsupported-direction", "Direction não suportada para estorno.");
    }

    String reason = "REVERSAL_OF:" + original.getId();
    if (cmd.reason() != null && !cmd.reason().isBlank()) {
      reason = reason + " :: " + cmd.reason();
    }

    StockMovement reversal =
        recordMovement(
            Direction.REVERSAL,
            reason,
            original.getPackagingId(),
            original.getBranchId(),
            original.getLocationId(),
            original.getBatchId(),
            original.getQuantity(),
            null,
            "reversal",
            original.getId(),
            null,
            cmd.actor().value());
    publishMovementRecorded(reversal, cmd.actor().value());
    log.info(
        "stock reversed original={} reversal={} actor={}",
        original.getId(),
        reversal.id().value(),
        cmd.actor().value());
    return reversal.id();
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Adquire lock pessimista no snapshot. Se não existir, cria zerado e tenta de novo — o INSERT
   * pode falhar por UNIQUE em concorrência extrema; nesse caso o re-find retorna a linha já criada
   * pela outra transação. Não usamos retry loop explícito pois o lock subsequente serializa.
   */
  private StockSnapshot lockOrCreate(
      UUID packagingId, UUID branchId, UUID locationId, UUID batchId) {
    Optional<StockSnapshot> existing =
        snapshots.findByKeyForUpdate(packagingId, branchId, locationId, batchId);
    if (existing.isPresent()) {
      return existing.get();
    }
    snapshots.saveAndFlush(StockSnapshot.zeroed(packagingId, branchId, locationId, batchId));
    return snapshots
        .findByKeyForUpdate(packagingId, branchId, locationId, batchId)
        .orElseThrow(
            () ->
                new IllegalStateException(
                    "snapshot recém-criado desapareceu — race com clear concorrente?"));
  }

  private StockSnapshot lockExisting(
      UUID packagingId, UUID branchId, UUID locationId, UUID batchId) {
    return snapshots
        .findByKeyForUpdate(packagingId, branchId, locationId, batchId)
        .orElseThrow(
            () ->
                new InvalidStockOperationException(
                    "snapshot-missing",
                    "Não existe saldo desta embalagem nesta filial/local/lote."));
  }

  private Optional<StockMovement> findByIdempotency(String key) {
    if (key == null || key.isBlank()) {
      return Optional.empty();
    }
    return movements.findByIdempotencyKey(key);
  }

  private StockMovement recordMovement(
      Direction direction,
      String reason,
      UUID packagingId,
      UUID branchId,
      UUID locationId,
      UUID batchId,
      BigDecimal quantity,
      BigDecimal unitCost,
      String originType,
      UUID originId,
      String idempotencyKey,
      UUID userId) {
    return movements.save(
        StockMovement.record(
            direction,
            reason,
            packagingId,
            branchId,
            locationId,
            batchId,
            quantity,
            unitCost,
            originType,
            originId,
            idempotencyKey,
            userId,
            null));
  }

  private void publishMovementRecorded(StockMovement m, UUID actor) {
    events.publishEvent(
        new StockMovementRecordedEvent(
            m.id(),
            currentTenantId(),
            m.packagingIdTyped(),
            m.branchIdTyped(),
            m.getDirection(),
            m.getQuantity(),
            m.getUnitCost(),
            m.getOriginType(),
            m.getOriginId(),
            new dev.dimi.erp.identidade.UserId(actor),
            m.getOccurredAt() == null ? Instant.now() : m.getOccurredAt()));
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
