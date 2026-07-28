package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.financeiro.FinancialRecordCancelledEvent;
import dev.dimi.erp.financeiro.FinancialRecordCreatedEvent;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.FinancialRecordRenegotiatedEvent;
import dev.dimi.erp.financeiro.FinancialRecordStatus;
import dev.dimi.erp.financeiro.InstallmentStatus;
import dev.dimi.erp.financeiro.internal.domain.FinancialApportionment;
import dev.dimi.erp.financeiro.internal.domain.FinancialInstallment;
import dev.dimi.erp.financeiro.internal.domain.FinancialRecord;
import dev.dimi.erp.financeiro.internal.persistence.FinancialApportionmentRepository;
import dev.dimi.erp.financeiro.internal.persistence.FinancialInstallmentRepository;
import dev.dimi.erp.financeiro.internal.persistence.FinancialRecordRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lifecycle de {@code financial_record} (MVF) — abertura, cancelamento e renegociação.
 *
 * <p>v1: pagamentos efetivos (parcial/total) e estorno entram no slice 7.3. Listeners cross-modulo
 * (SaleClosedEvent, PurchaseReceivedEvent, SupplierReturnIssuedEvent) entram no slice 7.3 também —
 * este service expõe {@link #open} como ponto de entrada que esses listeners vão consumir.
 *
 * <p>Idempotência tripla:
 *
 * <ul>
 *   <li>{@code idempotencyKey} no comando: se já existe MVF com a key, retorna a existente (sem
 *       inserir).
 *   <li>{@code (origin_type, origin_id)} único quando origin_type != MANUAL: idem.
 *   <li>{@code branch_id + number} único: retry on collision via {@link FinancialNumberSequencer}.
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManageFinancialRecordService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "financial_record";
  private static final BigDecimal CENT_TOLERANCE = new BigDecimal("0.01");

  private final FinancialRecordRepository records;
  private final FinancialInstallmentRepository installments;
  private final FinancialApportionmentRepository apportionments;
  private final FinancialNumberSequencer sequencer;
  private final ApplicationEventPublisher events;

  // ---------------------------------------------------------------------------
  // Open
  // ---------------------------------------------------------------------------

  @Transactional
  public FinancialRecordSummary open(OpenFinancialRecordCommand cmd) {
    validateOpenCommand(cmd);

    // Idempotência 1: chave explícita.
    if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
      var existing = records.findByIdempotencyKey(cmd.idempotencyKey());
      if (existing.isPresent()) {
        return summary(existing.get());
      }
    }
    // Idempotência 2: par (origin_type, origin_id) para origens automáticas.
    if (cmd.originType() != dev.dimi.erp.financeiro.FinancialOriginType.MANUAL
        && cmd.originId() != null) {
      var existing = records.findByOriginTypeAndOriginId(cmd.originType(), cmd.originId());
      if (existing.isPresent()) {
        return summary(existing.get());
      }
    }

    FinancialRecord saved = persistRecordWithRetry(cmd);
    List<FinancialInstallment> persistedInstallments = persistInstallments(saved, cmd);
    List<FinancialApportionment> persistedApportionments = persistApportionments(saved, cmd);

    events.publishEvent(
        new FinancialRecordCreatedEvent(
            saved.id(),
            currentTenantId(),
            saved.branchIdTyped(),
            saved.getDirection(),
            saved.counterpartyIdTyped(),
            saved.getOriginType(),
            saved.getOriginId(),
            saved.getTotalValue(),
            cmd.actor(),
            Instant.now()));
    log.info(
        "financial-record opened id={} branch={} number={} direction={} total={} actor={}",
        saved.id().value(),
        cmd.branchId().value(),
        saved.getNumber(),
        saved.getDirection(),
        saved.getTotalValue(),
        cmd.actor().value());

    return FinancialRecordSummary.of(
        saved,
        persistedInstallments.stream().map(FinancialInstallmentSummary::of).toList(),
        persistedApportionments.stream().map(FinancialApportionmentSummary::of).toList());
  }

  // ---------------------------------------------------------------------------
  // Cancel
  // ---------------------------------------------------------------------------

  @Transactional
  public FinancialRecordSummary cancel(CancelFinancialRecordCommand cmd) {
    if (cmd.reason() == null || cmd.reason().isBlank()) {
      throw new InvalidFinancialOperationException(
          "cancel-reason-required", "Motivo do cancelamento é obrigatório.");
    }
    FinancialRecord r = load(cmd.recordId());
    if (r.getStatus() != FinancialRecordStatus.OPEN) {
      throw new InvalidFinancialStateException("FinancialRecord", r.getStatus().name(), "cancel");
    }
    // Em 7.2 (sem pagamentos ainda), basta status=OPEN. Quando 7.3 adicionar pagamentos,
    // adicionar check de soma(paid_value) == 0 nas parcelas.

    r.markCancelled();
    cancelAllInstallments(r);

    events.publishEvent(
        new FinancialRecordCancelledEvent(
            r.id(), currentTenantId(), cmd.reason(), cmd.actor(), Instant.now()));
    log.info("financial-record cancelled id={} actor={}", r.id().value(), cmd.actor().value());
    return summary(r);
  }

  // ---------------------------------------------------------------------------
  // Renegotiate
  // ---------------------------------------------------------------------------

  @Transactional
  public FinancialRecordSummary renegotiate(RenegotiateFinancialRecordCommand cmd) {
    if (cmd.reason() == null || cmd.reason().isBlank()) {
      throw new InvalidFinancialOperationException(
          "renegotiate-reason-required", "Motivo da renegociação é obrigatório.");
    }
    if (cmd.newTotalValue() == null || cmd.newTotalValue().signum() <= 0) {
      throw new InvalidFinancialOperationException(
          "renegotiate-total-invalid", "newTotalValue deve ser positivo.");
    }
    if (cmd.newInstallments() == null || cmd.newInstallments().isEmpty()) {
      throw new InvalidFinancialOperationException(
          "renegotiate-empty-installments", "Informe pelo menos uma nova parcela.");
    }
    BigDecimal installmentsSum =
        cmd.newInstallments().stream()
            .map(RenegotiateFinancialRecordCommand.NewInstallmentLine::originalValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (installmentsSum.subtract(cmd.newTotalValue()).abs().compareTo(CENT_TOLERANCE) > 0) {
      throw new InvalidFinancialOperationException(
          "renegotiate-installments-mismatch",
          "Soma das novas parcelas ("
              + installmentsSum
              + ") difere de newTotalValue ("
              + cmd.newTotalValue()
              + ").");
    }

    FinancialRecord original = load(cmd.originalRecordId());
    if (original.getStatus() != FinancialRecordStatus.OPEN
        && original.getStatus() != FinancialRecordStatus.PARTIALLY_PAID) {
      throw new InvalidFinancialStateException(
          "FinancialRecord", original.getStatus().name(), "renegotiate");
    }

    BigDecimal remaining = openRemaining(original);

    FinancialRecord replacement = buildReplacementRecord(original, cmd);
    persistReplacementInstallments(replacement, cmd);
    copyApportionmentsFromOriginal(original, replacement, cmd.newTotalValue());

    original.markRenegotiated();
    markAllNonPaidInstallmentsRenegotiated(original);

    events.publishEvent(
        new FinancialRecordRenegotiatedEvent(
            original.id(),
            replacement.id(),
            currentTenantId(),
            remaining,
            cmd.newTotalValue(),
            cmd.reason(),
            cmd.actor(),
            Instant.now()));
    log.info(
        "financial-record renegotiated original={} replacement={} actor={}",
        original.id().value(),
        replacement.id().value(),
        cmd.actor().value());
    return summary(replacement);
  }

  // ---------------------------------------------------------------------------
  // Read
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public FinancialRecordSummary findById(FinancialRecordId recordId) {
    return summary(load(recordId));
  }

  @Transactional(readOnly = true)
  public Page<FinancialRecordSummary> listByBranchAndStatus(
      BranchId branchId, FinancialRecordStatus status, Pageable pageable) {
    // Repository foundation tem só findByBranchIdAndNumber; listing entra em slice futuro.
    // Por enquanto, expor um stub usando findAll filtrado em memória NÃO é aceitável; lançar
    // UnsupportedOperationException explícito e adicionar finder em slice posterior se for usado.
    throw new UnsupportedOperationException(
        "Listagem paginada requer adicionar finder no repository — entra em slice 7.x conforme demanda.");
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void validateOpenCommand(OpenFinancialRecordCommand cmd) {
    Objects.requireNonNull(cmd.branchId(), "branchId");
    Objects.requireNonNull(cmd.direction(), "direction");
    Objects.requireNonNull(cmd.counterpartyId(), "counterpartyId");
    Objects.requireNonNull(cmd.originType(), "originType");
    Objects.requireNonNull(cmd.totalValue(), "totalValue");
    if (cmd.totalValue().signum() <= 0) {
      throw new InvalidFinancialOperationException(
          "total-not-positive", "totalValue deve ser positivo.");
    }
    if (cmd.installments() == null || cmd.installments().isEmpty()) {
      throw new InvalidFinancialOperationException(
          "empty-installments", "Informe pelo menos uma parcela.");
    }
    if (cmd.apportionments() == null || cmd.apportionments().isEmpty()) {
      throw new InvalidFinancialOperationException(
          "empty-apportionments", "Informe pelo menos um rateio.");
    }
    BigDecimal installmentsSum =
        cmd.installments().stream()
            .map(OpenFinancialRecordCommand.InstallmentLine::originalValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (installmentsSum.subtract(cmd.totalValue()).abs().compareTo(CENT_TOLERANCE) > 0) {
      throw new InvalidFinancialOperationException(
          "installments-mismatch",
          "Soma das parcelas ("
              + installmentsSum
              + ") difere do total ("
              + cmd.totalValue()
              + ").");
    }
    BigDecimal apportionmentsSum =
        cmd.apportionments().stream()
            .map(OpenFinancialRecordCommand.ApportionmentLine::value)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (apportionmentsSum.subtract(cmd.totalValue()).abs().compareTo(CENT_TOLERANCE) > 0) {
      throw new InvalidFinancialOperationException(
          "apportionments-mismatch",
          "Soma dos rateios ("
              + apportionmentsSum
              + ") difere do total ("
              + cmd.totalValue()
              + ").");
    }
  }

  private FinancialRecord persistRecordWithRetry(OpenFinancialRecordCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(TABLE, cmd.branchId().value());
        FinancialRecord r =
            FinancialRecord.open(
                number,
                cmd.branchId().value(),
                cmd.direction(),
                cmd.counterpartyId().value(),
                cmd.documentNumber(),
                cmd.totalValue(),
                cmd.originType(),
                cmd.originId(),
                cmd.notes(),
                cmd.idempotencyKey());
        return records.saveAndFlush(r);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("financial-record number collision, retrying attempt={}", attempts);
      }
    }
  }

  private List<FinancialInstallment> persistInstallments(
      FinancialRecord record, OpenFinancialRecordCommand cmd) {
    List<FinancialInstallment> saved = new ArrayList<>();
    int seq = 1;
    for (OpenFinancialRecordCommand.InstallmentLine line : cmd.installments()) {
      saved.add(
          installments.save(
              FinancialInstallment.create(
                  record.id().value(),
                  seq++,
                  line.dueDate(),
                  line.originalValue(),
                  line.bankAccountId() == null ? null : line.bankAccountId().value(),
                  line.barcode())));
    }
    return saved;
  }

  private List<FinancialApportionment> persistApportionments(
      FinancialRecord record, OpenFinancialRecordCommand cmd) {
    List<FinancialApportionment> saved = new ArrayList<>();
    for (OpenFinancialRecordCommand.ApportionmentLine line : cmd.apportionments()) {
      saved.add(
          apportionments.save(
              FinancialApportionment.create(
                  record.id().value(),
                  line.chartAccountId().value(),
                  line.costCenterId() == null ? null : line.costCenterId().value(),
                  line.value(),
                  line.percentage(),
                  line.notes())));
    }
    return saved;
  }

  private void cancelAllInstallments(FinancialRecord record) {
    for (FinancialInstallment i :
        installments.findByRecordIdOrderBySequenceAsc(record.id().value())) {
      i.markCancelled();
    }
  }

  private void markAllNonPaidInstallmentsRenegotiated(FinancialRecord record) {
    for (FinancialInstallment i :
        installments.findByRecordIdOrderBySequenceAsc(record.id().value())) {
      if (i.getStatus() == InstallmentStatus.OPEN
          || i.getStatus() == InstallmentStatus.PARTIALLY_PAID) {
        i.markRenegotiated();
      }
    }
  }

  private BigDecimal openRemaining(FinancialRecord record) {
    return installments.findByRecordIdOrderBySequenceAsc(record.id().value()).stream()
        .filter(
            i ->
                i.getStatus() == InstallmentStatus.OPEN
                    || i.getStatus() == InstallmentStatus.PARTIALLY_PAID)
        .map(i -> i.getOriginalValue().subtract(i.getPaidValue()))
        .reduce(BigDecimal.ZERO, BigDecimal::add);
  }

  private FinancialRecord buildReplacementRecord(
      FinancialRecord original, RenegotiateFinancialRecordCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(TABLE, original.branchIdTyped().value());
        String notes =
            cmd.notes() == null ? "Renegociação de MVF " + original.getNumber() : cmd.notes();
        FinancialRecord r =
            FinancialRecord.open(
                number,
                original.branchIdTyped().value(),
                original.getDirection(),
                original.counterpartyIdTyped().value(),
                original.getDocumentNumber(),
                cmd.newTotalValue(),
                original.getOriginType(),
                original.getOriginId(),
                notes,
                null);
        return records.saveAndFlush(r);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("financial-record (renegotiation) number collision, attempt={}", attempts);
      }
    }
  }

  private void persistReplacementInstallments(
      FinancialRecord replacement, RenegotiateFinancialRecordCommand cmd) {
    int seq = 1;
    for (RenegotiateFinancialRecordCommand.NewInstallmentLine line : cmd.newInstallments()) {
      installments.save(
          FinancialInstallment.create(
              replacement.id().value(),
              seq++,
              line.dueDate(),
              line.originalValue(),
              line.bankAccountId() == null ? null : line.bankAccountId().value(),
              line.barcode()));
    }
  }

  /**
   * Copia rateios da MVF original para a substituta. Se {@code newTotal != originalTotal}, rateia
   * proporcionalmente: novo_valor = valor_orig * (newTotal / originalTotal). Diferença de
   * arredondamento vai pro primeiro rateio para garantir soma == newTotal.
   */
  private void copyApportionmentsFromOriginal(
      FinancialRecord original, FinancialRecord replacement, BigDecimal newTotal) {
    List<FinancialApportionment> origApps = apportionments.findByRecordId(original.id().value());
    BigDecimal origTotal = original.getTotalValue();
    BigDecimal accumulated = BigDecimal.ZERO;
    int last = origApps.size() - 1;
    for (int idx = 0; idx < origApps.size(); idx++) {
      FinancialApportionment app = origApps.get(idx);
      BigDecimal newValue;
      if (idx == last) {
        newValue = newTotal.subtract(accumulated);
      } else {
        newValue =
            app.getValue().multiply(newTotal).divide(origTotal, 2, java.math.RoundingMode.HALF_UP);
        accumulated = accumulated.add(newValue);
      }
      apportionments.save(
          FinancialApportionment.create(
              replacement.id().value(),
              app.chartAccountIdTyped().value(),
              app.costCenterIdTyped() == null ? null : app.costCenterIdTyped().value(),
              newValue,
              app.getPercentage(),
              app.getNotes()));
    }
  }

  private FinancialRecord load(FinancialRecordId recordId) {
    return records
        .findById(recordId.value())
        .orElseThrow(() -> new FinancialRecordNotFoundException(recordId));
  }

  private FinancialRecordSummary summary(FinancialRecord r) {
    List<FinancialInstallmentSummary> insts =
        installments.findByRecordIdOrderBySequenceAsc(r.id().value()).stream()
            .map(FinancialInstallmentSummary::of)
            .toList();
    List<FinancialApportionmentSummary> apps =
        apportionments.findByRecordId(r.id().value()).stream()
            .map(FinancialApportionmentSummary::of)
            .toList();
    return FinancialRecordSummary.of(r, insts, apps);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
