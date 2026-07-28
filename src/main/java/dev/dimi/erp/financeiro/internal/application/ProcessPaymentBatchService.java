package dev.dimi.erp.financeiro.internal.application;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.InstallmentStatus;
import dev.dimi.erp.financeiro.PaymentId;
import dev.dimi.erp.financeiro.PaymentProcessedEvent;
import dev.dimi.erp.financeiro.internal.domain.FinancialInstallment;
import dev.dimi.erp.financeiro.internal.domain.FinancialPayment;
import dev.dimi.erp.financeiro.internal.domain.FinancialPaymentApplication;
import dev.dimi.erp.financeiro.internal.domain.FinancialRecord;
import dev.dimi.erp.financeiro.internal.persistence.FinancialInstallmentRepository;
import dev.dimi.erp.financeiro.internal.persistence.FinancialPaymentApplicationRepository;
import dev.dimi.erp.financeiro.internal.persistence.FinancialPaymentRepository;
import dev.dimi.erp.financeiro.internal.persistence.FinancialRecordRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
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
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Processa batches de pagamento financeiro — 1 POST agrupa N aplicações de pagamento, todas
 * compartilhando {@code method} e {@code bankAccountId} (RN-FIN-007/008/009).
 *
 * <p>v1: cada batch gera exatamente 1 row em {@code financial_payment} (mesmo método/conta) com N
 * rows em {@code financial_payment_application}. Multi-método no mesmo POST fica para variante
 * futura ({@code batchId} já existe para agrupar N rows quando isso for necessário).
 *
 * <p>Idempotência por {@code idempotency_key} (header Idempotency-Key, obrigatório): repetir o
 * mesmo POST retorna o pagamento já processado sem reaplicar valores nas parcelas.
 *
 * <p>Após aplicar todas as parcelas do batch, recomputa o status das MVFs (records) tocadas a
 * partir do estado agregado das parcelas (PAID se todas pagas; PARTIALLY_PAID se alguma com
 * pagamento; OPEN caso contrário — estados terminais CANCELLED/RENEGOTIATED são preservados).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProcessPaymentBatchService {

  private static final int MAX_OPTIMISTIC_RETRIES = 3;

  private final FinancialRecordRepository records;
  private final FinancialInstallmentRepository installments;
  private final FinancialPaymentRepository payments;
  private final FinancialPaymentApplicationRepository applications;
  private final ApplicationEventPublisher events;
  private final TransactionTemplate tx;

  // ---------------------------------------------------------------------------
  // Process batch
  // ---------------------------------------------------------------------------

  /**
   * Processa o batch dentro de uma transação gerenciada por {@link TransactionTemplate}, com retry
   * em colisões de optimistic lock (versão otimista das parcelas é o ponto mais suscetível a
   * concorrência entre batches que aplicam pagamento na mesma parcela / MVF).
   */
  public FinancialPaymentSummary processBatch(ProcessPaymentBatchCommand cmd) {
    return runWithOptimisticRetry(() -> tx.execute(status -> processBatchInternal(cmd)));
  }

  private FinancialPaymentSummary processBatchInternal(ProcessPaymentBatchCommand cmd) {
    if (cmd.idempotencyKey() == null || cmd.idempotencyKey().isBlank()) {
      throw new InvalidFinancialOperationException(
          "idempotency-key-required",
          "Idempotency-Key é obrigatório em POST de batch de pagamento.");
    }
    if (cmd.applications() == null || cmd.applications().isEmpty()) {
      throw new InvalidFinancialOperationException(
          "empty-applications", "Informe pelo menos uma aplicação de pagamento.");
    }
    Objects.requireNonNull(cmd.bankAccountId(), "bankAccountId");
    Objects.requireNonNull(cmd.method(), "method");
    Objects.requireNonNull(cmd.actor(), "actor");

    // Idempotência: retorna o batch já processado se a key bater.
    var existing = payments.findByIdempotencyKey(cmd.idempotencyKey());
    if (existing.isPresent()) {
      return summary(existing.get());
    }

    Instant occurredAt = cmd.occurredAt() == null ? Instant.now() : cmd.occurredAt();

    // 1 batch = 1 row em financial_payment (v1; multi-método cria N rows).
    UUID batchId = UuidCreator.getTimeOrderedEpoch();
    FinancialPayment payment =
        payments.save(
            FinancialPayment.record(
                batchId,
                occurredAt,
                cmd.bankAccountId().value(),
                cmd.method(),
                cmd.actor().value(),
                cmd.notes(),
                cmd.idempotencyKey(),
                false));

    List<FinancialPaymentApplication> persistedApplications = new ArrayList<>();
    Set<UUID> touchedRecordIds = new LinkedHashSet<>();
    BigDecimal totalApplied = BigDecimal.ZERO;

    for (ProcessPaymentBatchCommand.Application line : cmd.applications()) {
      if (line.value() == null || line.value().signum() <= 0) {
        throw new InvalidFinancialOperationException(
            "application-value-not-positive", "value de aplicação deve ser positivo.");
      }
      FinancialInstallment installment =
          installments
              .findById(line.installmentId().value())
              .orElseThrow(
                  () ->
                      new InvalidFinancialOperationException(
                          "installment-not-found",
                          "Parcela não encontrada: " + line.installmentId().value()));
      InstallmentStatus status = installment.getStatus();
      if (status != InstallmentStatus.OPEN && status != InstallmentStatus.PARTIALLY_PAID) {
        throw new InvalidFinancialStateException(
            "FinancialInstallment", status.name(), "apply-payment");
      }

      installment.applyPayment(
          line.value(), line.fineApplied(), line.interestApplied(), line.discountApplied());

      FinancialPaymentApplication saved =
          applications.save(
              FinancialPaymentApplication.create(
                  payment.id().value(),
                  installment.id().value(),
                  line.value(),
                  nullToZero(line.fineApplied()),
                  nullToZero(line.interestApplied()),
                  nullToZero(line.discountApplied()),
                  false));
      persistedApplications.add(saved);

      touchedRecordIds.add(installment.getRecordId());
      totalApplied = totalApplied.add(line.value());
    }

    // Recomputa status dos records tocados a partir do agregado das parcelas.
    for (UUID recordId : touchedRecordIds) {
      recomputeRecordStatus(recordId);
    }

    events.publishEvent(
        new PaymentProcessedEvent(
            payment.batchIdTyped(),
            currentTenantId(),
            payment.bankAccountIdTyped(),
            payment.getMethod(),
            List.of(payment.id()),
            totalApplied,
            cmd.actor(),
            occurredAt));

    log.info(
        "financial-payment batch processed paymentId={} batchId={} method={} bankAccount={}"
            + " applications={} totalApplied={} actor={}",
        payment.id().value(),
        payment.batchIdTyped().value(),
        payment.getMethod(),
        payment.bankAccountIdTyped().value(),
        persistedApplications.size(),
        totalApplied,
        cmd.actor().value());

    return FinancialPaymentSummary.of(
        payment,
        persistedApplications.stream().map(FinancialPaymentApplicationSummary::of).toList());
  }

  // ---------------------------------------------------------------------------
  // Read
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public FinancialPaymentSummary findById(PaymentId paymentId) {
    FinancialPayment p =
        payments
            .findById(paymentId.value())
            .orElseThrow(() -> new FinancialPaymentNotFoundException(paymentId));
    return summary(p);
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
      // Tudo cancelado — não mexer no record (status já reflete cancelamento prévio).
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

  private static BigDecimal nullToZero(BigDecimal v) {
    return v == null ? BigDecimal.ZERO : v;
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
