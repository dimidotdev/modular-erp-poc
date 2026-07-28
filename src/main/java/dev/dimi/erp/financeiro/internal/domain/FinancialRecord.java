package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.financeiro.FinancialDirection;
import dev.dimi.erp.financeiro.FinancialOriginType;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.FinancialRecordStatus;
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
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * MVF — Movimentação Financeira (a pagar ou a receber). Tem 1..N parcelas e rateios obrigatórios.
 * Idempotência por par {@code (origin_type, origin_id)} e por {@code idempotency_key}.
 */
@Entity
@Table(name = "financial_record")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "number", "branchId", "direction", "status", "totalValue"})
public class FinancialRecord {

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

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, updatable = false)
  private FinancialDirection direction;

  @Column(name = "counterparty_id", nullable = false, updatable = false)
  private UUID counterpartyId;

  @Column(name = "document_number")
  private String documentNumber;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "total_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private FinancialRecordStatus status;

  @Enumerated(EnumType.STRING)
  @Column(name = "origin_type", nullable = false, updatable = false)
  private FinancialOriginType originType;

  @Column(name = "origin_id", updatable = false)
  private UUID originId;

  @Column(name = "notes")
  private String notes;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public FinancialRecordId id() {
    return new FinancialRecordId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public PersonId counterpartyIdTyped() {
    return new PersonId(counterpartyId);
  }

  public static FinancialRecord open(
      long number,
      UUID branchId,
      FinancialDirection direction,
      UUID counterpartyId,
      String documentNumber,
      BigDecimal totalValue,
      FinancialOriginType originType,
      UUID originId,
      String notes,
      String idempotencyKey) {
    FinancialRecord r = new FinancialRecord();
    r.id = UuidCreator.getTimeOrderedEpoch();
    r.number = number;
    r.branchId = Objects.requireNonNull(branchId, "branchId");
    r.direction = Objects.requireNonNull(direction, "direction");
    r.counterpartyId = Objects.requireNonNull(counterpartyId, "counterpartyId");
    r.documentNumber = documentNumber;
    r.totalValue = Objects.requireNonNull(totalValue, "totalValue");
    r.originType = Objects.requireNonNull(originType, "originType");
    r.originId = originId;
    r.notes = notes;
    r.idempotencyKey = idempotencyKey;
    r.issuedAt = Instant.now();
    r.status = FinancialRecordStatus.OPEN;
    return r;
  }

  public void markPartiallyPaid() {
    this.status = FinancialRecordStatus.PARTIALLY_PAID;
  }

  public void markPaid() {
    this.status = FinancialRecordStatus.PAID;
  }

  public void markCancelled() {
    this.status = FinancialRecordStatus.CANCELLED;
  }

  public void markRenegotiated() {
    this.status = FinancialRecordStatus.RENEGOTIATED;
  }

  public void updateNotes(String notes) {
    this.notes = notes;
  }

  /**
   * Recomputa o status do record a partir do estado agregado das parcelas. Caller (service) passa
   * dois booleans pré-calculados: {@code allPaid} (todas as parcelas não-canceladas estão PAID) e
   * {@code anyPaymentApplied} (alguma parcela tem paidValue > 0). Mantém estados terminais
   * (CANCELLED, RENEGOTIATED).
   */
  public void recomputeStatusFromInstallments(boolean allPaid, boolean anyPaymentApplied) {
    if (this.status == dev.dimi.erp.financeiro.FinancialRecordStatus.CANCELLED
        || this.status == dev.dimi.erp.financeiro.FinancialRecordStatus.RENEGOTIATED) {
      return;
    }
    if (allPaid) {
      this.status = dev.dimi.erp.financeiro.FinancialRecordStatus.PAID;
    } else if (anyPaymentApplied) {
      this.status = dev.dimi.erp.financeiro.FinancialRecordStatus.PARTIALLY_PAID;
    } else {
      this.status = dev.dimi.erp.financeiro.FinancialRecordStatus.OPEN;
    }
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
    if (issuedAt == null) {
      issuedAt = now;
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
    if (!(o instanceof FinancialRecord other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
