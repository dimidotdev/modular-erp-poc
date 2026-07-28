package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.InstallmentId;
import dev.dimi.erp.financeiro.PaymentId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Aplicação de um {@link FinancialPayment} em uma {@link FinancialInstallment}, com juros/multa/
 * desconto efetivamente cobrados.
 */
@Entity
@Table(name = "financial_payment_application")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "paymentId", "installmentId", "value", "isReversal"})
public class FinancialPaymentApplication {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "payment_id", nullable = false, updatable = false)
  private UUID paymentId;

  @Column(name = "installment_id", nullable = false, updatable = false)
  private UUID installmentId;

  @Column(name = "value", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal value;

  @Column(name = "fine_applied", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal fineApplied;

  @Column(name = "interest_applied", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal interestApplied;

  @Column(name = "discount_applied", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal discountApplied;

  @Column(name = "is_reversal", nullable = false, updatable = false)
  private boolean isReversal;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public PaymentApplicationId id() {
    return new PaymentApplicationId(id);
  }

  public PaymentId paymentIdTyped() {
    return new PaymentId(paymentId);
  }

  public InstallmentId installmentIdTyped() {
    return new InstallmentId(installmentId);
  }

  public static FinancialPaymentApplication create(
      UUID paymentId,
      UUID installmentId,
      BigDecimal value,
      BigDecimal fineApplied,
      BigDecimal interestApplied,
      BigDecimal discountApplied,
      boolean isReversal) {
    FinancialPaymentApplication a = new FinancialPaymentApplication();
    a.id = UuidCreator.getTimeOrderedEpoch();
    a.paymentId = Objects.requireNonNull(paymentId, "paymentId");
    a.installmentId = Objects.requireNonNull(installmentId, "installmentId");
    a.value = Objects.requireNonNull(value, "value");
    a.fineApplied = Objects.requireNonNull(fineApplied, "fineApplied");
    a.interestApplied = Objects.requireNonNull(interestApplied, "interestApplied");
    a.discountApplied = Objects.requireNonNull(discountApplied, "discountApplied");
    a.isReversal = isReversal;
    return a;
  }

  @PrePersist
  void onPersist() {
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FinancialPaymentApplication other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
