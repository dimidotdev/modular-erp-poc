package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.InstallmentStatus;
import dev.dimi.erp.financeiro.PaymentReversedEvent;
import dev.dimi.erp.financeiro.internal.domain.FinancialInstallment;
import dev.dimi.erp.financeiro.internal.domain.FinancialPayment;
import dev.dimi.erp.financeiro.internal.domain.FinancialPaymentApplication;
import dev.dimi.erp.financeiro.internal.domain.FinancialRecord;
import dev.dimi.erp.financeiro.internal.domain.FinancialReversal;
import dev.dimi.erp.financeiro.internal.persistence.FinancialInstallmentRepository;
import dev.dimi.erp.financeiro.internal.persistence.FinancialPaymentApplicationRepository;
import dev.dimi.erp.financeiro.internal.persistence.FinancialPaymentRepository;
import dev.dimi.erp.financeiro.internal.persistence.FinancialRecordRepository;
import dev.dimi.erp.financeiro.internal.persistence.FinancialReversalRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Estorna um {@link FinancialPayment} previamente processado emitindo um pagamento espelho com
 * valores invertidos (RN-FIN-013).
 *
 * <p>Mecanismo:
 *
 * <ol>
 *   <li>Cria nova row em {@code financial_payment} preservando {@code batchId}, {@code
 *       bankAccountId} e {@code method} do pagamento original (id novo, {@code occurredAt = now},
 *       autor = ator do estorno).
 *   <li>Para cada {@link FinancialPaymentApplication} do pagamento original, cria aplicação espelho
 *       no pagamento novo com {@code value/fineApplied/interestApplied/discountApplied} negados, e
 *       chama {@link FinancialInstallment#revertPayment} na parcela passando os valores positivos
 *       originais (o método já subtrai).
 *   <li>Persiste {@link FinancialReversal} (constraint {@code UNIQUE(original_payment_id)} garante
 *       que um pagamento só pode ser estornado uma única vez).
 *   <li>Recomputa o status de cada MVF tocada (mesma lógica usada em {@link
 *       ProcessPaymentBatchService}).
 *   <li>Publica {@link PaymentReversedEvent}.
 * </ol>
 *
 * <p>Idempotência: lookup por {@link FinancialReversalRepository#findByOriginalPaymentId} — se o
 * pagamento original já foi estornado, retorna o summary do pagamento espelho existente sem efeitos
 * colaterais.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReverseFinancialPaymentService {

  private static final int MAX_OPTIMISTIC_RETRIES = 3;

  private final FinancialPaymentRepository payments;
  private final FinancialPaymentApplicationRepository applications;
  private final FinancialInstallmentRepository installments;
  private final FinancialRecordRepository records;
  private final FinancialReversalRepository reversals;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;

  // ---------------------------------------------------------------------------
  // Reverse
  // ---------------------------------------------------------------------------

  /**
   * Estorno com retry em colisões de optimistic lock — a versão otimista das parcelas pode colidir
   * com batches concorrentes aplicando pagamento na mesma parcela / MVF.
   */
  public FinancialPaymentSummary reverse(ReverseFinancialPaymentCommand cmd) {
    return runWithOptimisticRetry(() -> tx.execute(status -> reverseInternal(cmd)));
  }

  private FinancialPaymentSummary reverseInternal(ReverseFinancialPaymentCommand cmd) {
    Objects.requireNonNull(cmd, "cmd");
    Objects.requireNonNull(cmd.originalPaymentId(), "originalPaymentId");
    Objects.requireNonNull(cmd.actor(), "actor");
    if (cmd.reason() == null || cmd.reason().isBlank()) {
      throw new InvalidFinancialOperationException(
          "reverse-reason-required", "Motivo do estorno é obrigatório.");
    }

    UUID originalId = cmd.originalPaymentId().value();

    // Idempotência: estorno já registrado para este pagamento? Retorna o espelho existente.
    var existingReversal = reversals.findByOriginalPaymentId(originalId);
    if (existingReversal.isPresent()) {
      FinancialReversal r = existingReversal.get();
      FinancialPayment mirror =
          payments
              .findById(r.getReversalPaymentId())
              .orElseThrow(() -> new FinancialPaymentNotFoundException(r.reversalPaymentIdTyped()));
      return summary(mirror);
    }

    // Carrega pagamento original.
    FinancialPayment original =
        payments
            .findById(originalId)
            .orElseThrow(() -> new FinancialPaymentNotFoundException(cmd.originalPaymentId()));

    // Carrega aplicações originais.
    List<FinancialPaymentApplication> originalApplications =
        applications.findByPaymentId(originalId);

    Instant occurredAt = Instant.now();

    // Cria pagamento espelho (mesmo batch/bankAccount/method, id novo, sem idempotency-key
    // colidindo: usa "reversal:" + originalId).
    FinancialPayment mirror =
        payments.saveAndFlush(
            FinancialPayment.record(
                original.getBatchId(),
                occurredAt,
                original.getBankAccountId(),
                original.getMethod(),
                cmd.actor().value(),
                "Estorno de pagamento " + originalId + ": " + cmd.reason(),
                "reversal:" + originalId,
                true));

    // Espelha cada aplicação com valores negados e reverte a parcela.
    Set<UUID> touchedRecordIds = new LinkedHashSet<>();
    BigDecimal totalReversed = BigDecimal.ZERO;
    for (FinancialPaymentApplication origApp : originalApplications) {
      FinancialInstallment installment =
          installments
              .findById(origApp.getInstallmentId())
              .orElseThrow(
                  () ->
                      new InvalidFinancialOperationException(
                          "installment-not-found",
                          "Parcela não encontrada: " + origApp.getInstallmentId()));

      installment.revertPayment(
          origApp.getValue(),
          origApp.getFineApplied(),
          origApp.getInterestApplied(),
          origApp.getDiscountApplied());

      applications.save(
          FinancialPaymentApplication.create(
              mirror.id().value(),
              installment.id().value(),
              origApp.getValue().negate(),
              origApp.getFineApplied().negate(),
              origApp.getInterestApplied().negate(),
              origApp.getDiscountApplied().negate(),
              true));

      touchedRecordIds.add(installment.getRecordId());
      totalReversed = totalReversed.add(origApp.getValue());
    }

    // Persiste registro de estorno (UNIQUE(original_payment_id) protege contra duplo estorno).
    FinancialReversal reversal =
        reversals.save(
            FinancialReversal.issue(
                originalId, mirror.id().value(), cmd.reason(), cmd.actor().value()));

    // Recomputa status das MVFs tocadas.
    for (UUID recordId : touchedRecordIds) {
      recomputeRecordStatus(recordId);
    }

    events.publishEvent(
        new PaymentReversedEvent(
            reversal.id().value(),
            original.id(),
            mirror.id(),
            currentTenantId(),
            cmd.reason(),
            cmd.actor(),
            occurredAt));

    log.info(
        "financial-payment reversed reversalId={} originalPaymentId={} reversalPaymentId={}"
            + " applications={} totalReversed={} actor={}",
        reversal.id().value(),
        original.id().value(),
        mirror.id().value(),
        originalApplications.size(),
        totalReversed,
        cmd.actor().value());

    return summary(mirror);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private void recomputeRecordStatus(UUID recordId) {
    List<FinancialInstallment> all = installments.findByRecordIdOrderBySequenceAsc(recordId);
    boolean allPaid = true;
    boolean anyPaymentApplied = false;
    boolean hasNonCancelled = false;
    for (FinancialInstallment i : all) {
      if (i.getStatus() == InstallmentStatus.CANCELLED) {
        continue;
      }
      hasNonCancelled = true;
      if (i.getStatus() != InstallmentStatus.PAID) {
        allPaid = false;
      }
      if (i.getPaidValue() != null && i.getPaidValue().signum() > 0) {
        anyPaymentApplied = true;
      }
    }
    if (!hasNonCancelled) {
      return;
    }
    FinancialRecord record =
        records
            .findById(recordId)
            .orElseThrow(
                () ->
                    new FinancialRecordNotFoundException(
                        new dev.dimi.erp.financeiro.FinancialRecordId(recordId)));
    record.recomputeStatusFromInstallments(allPaid, anyPaymentApplied);
  }

  private FinancialPaymentSummary summary(FinancialPayment p) {
    List<FinancialPaymentApplicationSummary> apps =
        applications.findByPaymentId(p.id().value()).stream()
            .map(FinancialPaymentApplicationSummary::of)
            .toList();
    return FinancialPaymentSummary.of(p, apps);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }

  /**
   * Executa {@code op} com retry simples em {@link ObjectOptimisticLockingFailureException} /
   * {@link jakarta.persistence.OptimisticLockException}. O retry só faz sentido porque a transação
   * é gerenciada pelo {@link TransactionTemplate} dentro de {@code op}: cada tentativa abre uma
   * transação nova e relê as parcelas com a versão atual.
   */
  private <T> T runWithOptimisticRetry(java.util.function.Supplier<T> op) {
    int attempts = 0;
    while (true) {
      try {
        attempts++;
        return op.get();
      } catch (ObjectOptimisticLockingFailureException
          | jakarta.persistence.OptimisticLockException e) {
        if (attempts >= MAX_OPTIMISTIC_RETRIES) {
          throw e;
        }
        log.warn(
            "optimistic-lock collision on attempt {}, retrying ({})", attempts, e.getMessage());
      }
    }
  }
}
