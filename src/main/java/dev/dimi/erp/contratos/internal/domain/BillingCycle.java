package dev.dimi.erp.contratos.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.contratos.BillingCycleId;
import dev.dimi.erp.contratos.BillingCycleStatus;
import dev.dimi.erp.contratos.ContractId;
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
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Ciclo de faturamento mensal de um contrato — espelha {@code contract_billing_cycle}. O job
 * recorrente do slice 10.2 cria um registro {@code PENDING} por contrato+competência; o listener
 * financeiro (ou refinement futuro) materializa o MVF a receber e marca como {@code GENERATED},
 * preenchendo {@code financialRecordId} e (quando aplicável) {@code invoiceId} da NFSe.
 *
 * <p>UNIQUE {@code (contract_id, competence_month)} no banco impede duplicação por mês de
 * competência — caller deve consultar via {@code findByContractIdAndCompetenceMonth} antes de
 * criar.
 *
 * <p>Estados seguem {@link BillingCycleStatus}:
 *
 * <ul>
 *   <li>{@code PENDING}: criado pelo job, aguardando geração.
 *   <li>{@code GENERATED}: MVF criado; {@code financialRecordId} populado.
 *   <li>{@code FAILED}: geração falhou; elegível para {@link #resetForRegeneration()}.
 *   <li>{@code SKIPPED}: ciclo intencionalmente ignorado (contrato suspenso, etc).
 * </ul>
 *
 * <p>FKs a {@code contract}, {@code financial_record} e {@code invoice} ficam como UUID raw
 * (não @ManyToOne) seguindo o padrão das demais entidades do módulo — evita cascata acidental e
 * lazy fetch desnecessário.
 */
@Entity
@Table(name = "contract_billing_cycle")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "contractId", "competenceMonth", "dueDate", "status"})
public class BillingCycle {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "contract_id", nullable = false, updatable = false)
  private UUID contractId;

  @Column(name = "competence_month", nullable = false, updatable = false)
  private LocalDate competenceMonth;

  @Column(name = "due_date", nullable = false, updatable = false)
  private LocalDate dueDate;

  @Column(name = "value", nullable = false, updatable = false, precision = 15, scale = 2)
  private BigDecimal value;

  @Column(name = "financial_record_id")
  private UUID financialRecordId;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private BillingCycleStatus status;

  @Column(name = "generated_at")
  private Instant generatedAt;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public BillingCycleId typedId() {
    return new BillingCycleId(id);
  }

  public ContractId contractIdTyped() {
    return new ContractId(contractId);
  }

  /**
   * Agenda novo ciclo {@code PENDING}. Caller (job de scheduling) garante unicidade por {@code
   * (contractId, competenceMonth)} via consulta prévia.
   */
  public static BillingCycle schedule(
      UUID contractId, LocalDate competenceMonth, LocalDate dueDate, BigDecimal value) {
    BillingCycle c = new BillingCycle();
    c.id = UuidCreator.getTimeOrderedEpoch();
    c.contractId = Objects.requireNonNull(contractId, "contractId");
    c.competenceMonth = Objects.requireNonNull(competenceMonth, "competenceMonth");
    c.dueDate = Objects.requireNonNull(dueDate, "dueDate");
    c.value = Objects.requireNonNull(value, "value");
    c.status = BillingCycleStatus.PENDING;
    return c;
  }

  /**
   * Marca ciclo como {@code GENERATED} após o financeiro criar o MVF. {@code financialRecordId} e
   * {@code invoiceId} são opcionais — slice 10.2 pode passar ambos como null e refinement futuro
   * preenche conforme listeners cross-module amadureçam.
   *
   * @throws IllegalStateException se status atual não for {@code PENDING}
   */
  public void markGenerated(Instant when, UUID financialRecordId, UUID invoiceId) {
    if (this.status != BillingCycleStatus.PENDING) {
      throw new IllegalStateException(
          "ciclo não pode ser marcado como GENERATED a partir de " + this.status);
    }
    this.status = BillingCycleStatus.GENERATED;
    this.generatedAt = Objects.requireNonNull(when, "when");
    this.financialRecordId = financialRecordId;
    this.invoiceId = invoiceId;
    this.failureReason = null;
  }

  /**
   * Marca ciclo como {@code FAILED}. Aceita transição a partir de {@code PENDING} (primeira falha)
   * ou {@code FAILED} (re-tentativa que falhou de novo). {@code generatedAt} é zerado para
   * preservar invariante "GENERATED ⇒ generatedAt não nulo".
   *
   * @throws IllegalStateException se status atual não for {@code PENDING} nem {@code FAILED}
   */
  public void markFailed(String reason) {
    if (this.status != BillingCycleStatus.PENDING && this.status != BillingCycleStatus.FAILED) {
      throw new IllegalStateException(
          "ciclo não pode ser marcado como FAILED a partir de " + this.status);
    }
    this.status = BillingCycleStatus.FAILED;
    this.failureReason = reason;
    this.generatedAt = null;
  }

  /**
   * Marca ciclo como {@code SKIPPED} — usado quando o contrato é suspenso após o ciclo já ter sido
   * criado como {@code PENDING}. {@code reason} entra em {@code notes} para auditoria.
   *
   * @throws IllegalStateException se status atual não for {@code PENDING}
   */
  public void markSkipped(String reason) {
    if (this.status != BillingCycleStatus.PENDING) {
      throw new IllegalStateException(
          "ciclo não pode ser marcado como SKIPPED a partir de " + this.status);
    }
    this.status = BillingCycleStatus.SKIPPED;
    this.notes = reason;
  }

  /** Indica se o ciclo está em estado elegível para regeneração manual. */
  public boolean canRegenerate() {
    return this.status == BillingCycleStatus.FAILED;
  }

  /**
   * Reseta o ciclo para {@code PENDING} após falha, permitindo que o caller re-execute a lógica de
   * geração. Não dispara a geração — apenas restaura estado.
   *
   * @throws IllegalStateException se {@link #canRegenerate()} for {@code false}
   */
  public void resetForRegeneration() {
    if (!canRegenerate()) {
      throw new IllegalStateException(
          "ciclo em status " + this.status + " não pode ser regenerado");
    }
    this.status = BillingCycleStatus.PENDING;
    this.failureReason = null;
    this.generatedAt = null;
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
    if (!(o instanceof BillingCycle other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
