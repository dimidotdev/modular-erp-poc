package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.PaymentId;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Estorno financeiro: registra que um pagamento original foi anulado por outro pagamento espelho
 * (com valores invertidos). A constraint {@code UNIQUE(original_payment_id)} garante que um mesmo
 * pagamento só pode ser estornado uma única vez.
 */
@Entity
@Table(name = "financial_reversal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "originalPaymentId", "reversalPaymentId", "occurredAt"})
public class FinancialReversal {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "original_payment_id", nullable = false, updatable = false)
  private UUID originalPaymentId;

  @Column(name = "reversal_payment_id", nullable = false, updatable = false)
  private UUID reversalPaymentId;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public ReversalId id() {
    return new ReversalId(id);
  }

  public PaymentId originalPaymentIdTyped() {
    return new PaymentId(originalPaymentId);
  }

  public PaymentId reversalPaymentIdTyped() {
    return new PaymentId(reversalPaymentId);
  }

  public UserId userIdTyped() {
    return new UserId(userId);
  }

  public static FinancialReversal issue(
      UUID originalPaymentId, UUID reversalPaymentId, String reason, UUID userId) {
    FinancialReversal r = new FinancialReversal();
    r.id = UuidCreator.getTimeOrderedEpoch();
    r.originalPaymentId = Objects.requireNonNull(originalPaymentId, "originalPaymentId");
    r.reversalPaymentId = Objects.requireNonNull(reversalPaymentId, "reversalPaymentId");
    r.reason = Objects.requireNonNull(reason, "reason");
    r.userId = Objects.requireNonNull(userId, "userId");
    r.occurredAt = Instant.now();
    return r;
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (occurredAt == null) {
      occurredAt = now;
    }
    if (createdAt == null) {
      createdAt = now;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FinancialReversal other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
