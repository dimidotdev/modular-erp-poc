package dev.dimi.erp.contratos.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractStatus;
import dev.dimi.erp.contratos.InvalidContractTransitionException;
import dev.dimi.erp.contratos.Recurrence;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Contrato (recorrência de faturamento). Slice 10.1: campos descritivos + ciclo de vida +
 * preparação para aditivos/reajustes. Geração de {@code billing_cycle}s e listener financeiro
 * entram em 10.2.
 *
 * <p>{@link #version} presente desde o dia 1 — transições simultâneas (suspend/resume/terminate)
 * via API podem corromper status sem optimistic locking. Mesmo padrão da {@code ServiceOrder}.
 */
@Entity
@Table(name = "contract")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "number", "branchId", "status"})
public class Contract {

  private static final EnumSet<ContractStatus> TERMINABLE_FROM =
      EnumSet.of(ContractStatus.ACTIVE, ContractStatus.SUSPENDED);

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "contact_id")
  private UUID contactId;

  @Setter
  @Column(name = "title", nullable = false)
  private String title;

  @Setter
  @Column(name = "description")
  private String description;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private ContractStatus status;

  @Column(name = "start_date", nullable = false, updatable = false)
  private LocalDate startDate;

  @Setter
  @Column(name = "end_date")
  private LocalDate endDate;

  @Setter
  @Column(name = "auto_renew", nullable = false)
  private boolean autoRenew;

  @Setter
  @Column(name = "renewal_period_months")
  private Integer renewalPeriodMonths;

  @Enumerated(EnumType.STRING)
  @Column(name = "recurrence", nullable = false)
  private Recurrence recurrence;

  @Column(name = "billing_day", nullable = false)
  private int billingDay;

  @Setter
  @Column(name = "payment_condition_id")
  private UUID paymentConditionId;

  /**
   * Valor base do contrato — mutável apenas via {@link #updateBaseValue} (edição em DRAFT ou
   * aditivo VALUE) e {@link #applyAdjustment} (reajuste por índice). Sem {@code @Setter} para
   * forçar uso dos métodos de domínio que carregam a regra correta.
   */
  @Column(name = "base_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal baseValue;

  @Column(name = "last_adjustment_date")
  private LocalDate lastAdjustmentDate;

  @Column(name = "next_adjustment_date")
  private LocalDate nextAdjustmentDate;

  @Setter
  @Column(name = "cost_center_id")
  private UUID costCenterId;

  @Setter
  @Column(name = "cancellation_clause")
  private String cancellationClause;

  @Setter
  @Column(name = "cancellation_fee_value", precision = 15, scale = 2)
  private BigDecimal cancellationFeeValue;

  @Setter
  @Column(name = "cancellation_notice_days")
  private Integer cancellationNoticeDays;

  @Setter
  @Column(name = "notes")
  private String notes;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "activated_at")
  private Instant activatedAt;

  @Column(name = "suspended_at")
  private Instant suspendedAt;

  @Column(name = "terminated_at")
  private Instant terminatedAt;

  @Column(name = "termination_reason")
  private String terminationReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public ContractId typedId() {
    return new ContractId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public PersonId customerIdTyped() {
    return new PersonId(customerId);
  }

  /**
   * Cria contrato em {@code DRAFT}. Todas as validações de invariantes de ativação (itens, valores,
   * datas coerentes) ficam para {@link #activate(UserId)} — em DRAFT a entidade ainda está sendo
   * montada.
   */
  public static Contract draft(
      long number,
      BranchId branchId,
      PersonId customerId,
      UUID contactId,
      String title,
      String description,
      LocalDate startDate,
      LocalDate endDate,
      Recurrence recurrence,
      int billingDay,
      BigDecimal baseValue,
      UUID paymentConditionId,
      UUID costCenterId,
      boolean autoRenew,
      Integer renewalPeriodMonths,
      String cancellationClause,
      BigDecimal cancellationFeeValue,
      Integer cancellationNoticeDays,
      String notes,
      UserId createdBy) {
    Contract c = new Contract();
    c.id = UuidCreator.getTimeOrderedEpoch();
    c.number = number;
    c.branchId = Objects.requireNonNull(branchId, "branchId").value();
    c.customerId = Objects.requireNonNull(customerId, "customerId").value();
    c.contactId = contactId;
    c.title = Objects.requireNonNull(title, "title");
    c.description = description;
    c.status = ContractStatus.DRAFT;
    c.startDate = Objects.requireNonNull(startDate, "startDate");
    c.endDate = endDate;
    c.recurrence = Objects.requireNonNull(recurrence, "recurrence");
    c.billingDay = billingDay;
    c.baseValue = Objects.requireNonNull(baseValue, "baseValue").setScale(2, RoundingMode.HALF_UP);
    c.paymentConditionId = paymentConditionId;
    c.costCenterId = costCenterId;
    c.autoRenew = autoRenew;
    c.renewalPeriodMonths = renewalPeriodMonths;
    c.cancellationClause = cancellationClause;
    c.cancellationFeeValue = cancellationFeeValue;
    c.cancellationNoticeDays = cancellationNoticeDays;
    c.notes = notes;
    c.createdBy = Objects.requireNonNull(createdBy, "createdBy").value();
    return c;
  }

  /**
   * Ativa contrato. Validações de invariantes (itens não vazios, baseValue > 0, billingDay válido,
   * datas coerentes) ficam no service de application — entidade só garante a transição válida.
   * Caller deve disparar {@code ContractActivatedEvent} após persist.
   */
  public void activate(UserId by) {
    if (this.status != ContractStatus.DRAFT) {
      throw new InvalidContractTransitionException(this.status, ContractStatus.ACTIVE);
    }
    Objects.requireNonNull(by, "by");
    this.status = ContractStatus.ACTIVE;
    this.activatedAt = Instant.now();
  }

  /**
   * Suspende contrato ativo. Geração de novos billing cycles deve pausar até {@link
   * #resume(UserId)}. {@code reason} é appendado em {@code notes} para rastreabilidade simples (v1
   * — registro estruturado de motivo entra com listener de auditoria).
   */
  public void suspend(UserId by, String reason) {
    if (this.status != ContractStatus.ACTIVE) {
      throw new InvalidContractTransitionException(this.status, ContractStatus.SUSPENDED);
    }
    Objects.requireNonNull(by, "by");
    this.status = ContractStatus.SUSPENDED;
    this.suspendedAt = Instant.now();
    if (reason != null && !reason.isBlank()) {
      this.notes =
          (this.notes == null || this.notes.isBlank())
              ? "[SUSPEND] " + reason
              : this.notes + "\n[SUSPEND] " + reason;
    }
  }

  /** Retoma contrato suspenso. Limpa {@code suspendedAt} para sinalizar resume. */
  public void resume(UserId by) {
    if (this.status != ContractStatus.SUSPENDED) {
      throw new InvalidContractTransitionException(this.status, ContractStatus.ACTIVE);
    }
    Objects.requireNonNull(by, "by");
    this.status = ContractStatus.ACTIVE;
    this.suspendedAt = null;
  }

  /**
   * Encerra contrato. Se {@code early=true}, vira {@code TERMINATED_EARLY} (com multa aplicável via
   * {@link #cancellationFeeValue}); caso contrário {@code EXPIRED} (encerramento natural por fim de
   * vigência). Aceita partir de {@code ACTIVE} ou {@code SUSPENDED}.
   */
  public void terminate(UserId by, LocalDate terminationDate, String reason, boolean early) {
    if (!TERMINABLE_FROM.contains(this.status)) {
      throw new InvalidContractTransitionException(
          this.status, early ? ContractStatus.TERMINATED_EARLY : ContractStatus.EXPIRED);
    }
    Objects.requireNonNull(by, "by");
    Objects.requireNonNull(terminationDate, "terminationDate");
    this.status = early ? ContractStatus.TERMINATED_EARLY : ContractStatus.EXPIRED;
    this.terminatedAt = Instant.now();
    this.terminationReason = reason;
  }

  /**
   * Marca contrato como {@code EXPIRED} — usado quando aditivo de {@code EXTENSION} estabelece
   * {@code newEndDate} no passado, ou quando job de housekeeping detecta {@code endDate} vencido
   * sem {@code autoRenew}. TODO 10.2: invocar via job diário.
   */
  public void expire() {
    if (this.status != ContractStatus.ACTIVE) {
      throw new InvalidContractTransitionException(this.status, ContractStatus.EXPIRED);
    }
    this.status = ContractStatus.EXPIRED;
    this.terminatedAt = Instant.now();
  }

  /**
   * Altera {@code baseValue} — uso negocial. Aplicável em {@code DRAFT} (edição direta) ou em
   * {@code ACTIVE} via aditivo {@code VALUE}. NÃO atualiza {@code lastAdjustmentDate} (isso é
   * exclusivo de {@link #applyAdjustment}, que rastreia reajuste por índice). {@code effectiveDate}
   * é informativo para o caller registrar no aditivo.
   */
  public void updateBaseValue(BigDecimal newValue, LocalDate effectiveDate) {
    Objects.requireNonNull(newValue, "newValue");
    Objects.requireNonNull(effectiveDate, "effectiveDate");
    this.baseValue = newValue.setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Aplica reajuste por índice (IGPM/IPCA/INPC/MANUAL). Sobe {@link #baseValue} pelo {@code
   * percentage}, atualiza {@link #lastAdjustmentDate} e calcula {@link #nextAdjustmentDate} para
   * {@code appliedOn + 12 meses} (cadência anual padrão BR). Caller (service) é responsável por
   * persistir {@code Adjustment} com {@code oldBaseValue}/{@code newBaseValue} para auditoria.
   */
  public void applyAdjustment(BigDecimal percentage, LocalDate appliedOn) {
    Objects.requireNonNull(percentage, "percentage");
    Objects.requireNonNull(appliedOn, "appliedOn");
    BigDecimal factor =
        BigDecimal.ONE.add(percentage.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
    this.baseValue = this.baseValue.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    this.lastAdjustmentDate = appliedOn;
    this.nextAdjustmentDate = appliedOn.plusMonths(12);
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (createdAt == null) {
      createdAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Contract other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
