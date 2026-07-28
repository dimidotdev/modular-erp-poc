package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.BatchId;
import dev.dimi.erp.financeiro.PaymentId;
import dev.dimi.erp.financeiro.PaymentMethod;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Registro de quitação. 1 batch (POST /payments/batch) pode produzir N rows quando paga múltiplas
 * MVFs em métodos distintos — {@code batchId} agrupa.
 */
@Entity
@Table(name = "financial_payment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "batchId", "method", "bankAccountId", "occurredAt", "isReversal"})
public class FinancialPayment {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "batch_id", nullable = false, updatable = false)
  private UUID batchId;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Column(name = "bank_account_id", nullable = false, updatable = false)
  private UUID bankAccountId;

  @Enumerated(EnumType.STRING)
  @Column(name = "method", nullable = false, updatable = false)
  private PaymentMethod method;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "notes")
  private String notes;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Column(name = "is_reversal", nullable = false, updatable = false)
  private boolean isReversal;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public PaymentId id() {
    return new PaymentId(id);
  }

  public BatchId batchIdTyped() {
    return new BatchId(batchId);
  }

  public BankAccountId bankAccountIdTyped() {
    return new BankAccountId(bankAccountId);
  }

  public UserId userIdTyped() {
    return new UserId(userId);
  }

  public static FinancialPayment record(
      UUID batchId,
      Instant occurredAt,
      UUID bankAccountId,
      PaymentMethod method,
      UUID userId,
      String notes,
      String idempotencyKey,
      boolean isReversal) {
    FinancialPayment p = new FinancialPayment();
    p.id = UuidCreator.getTimeOrderedEpoch();
    p.batchId = Objects.requireNonNull(batchId, "batchId");
    p.occurredAt = occurredAt;
    p.bankAccountId = Objects.requireNonNull(bankAccountId, "bankAccountId");
    p.method = Objects.requireNonNull(method, "method");
    p.userId = Objects.requireNonNull(userId, "userId");
    p.notes = notes;
    p.idempotencyKey = idempotencyKey;
    p.isReversal = isReversal;
    return p;
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
    if (occurredAt == null) {
      occurredAt = now;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FinancialPayment other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
