package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.InstallmentId;
import dev.dimi.erp.financeiro.InstallmentStatus;
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
 * Parcela de uma MVF. Acumula valores pagos/multa/juros/desconto. Status espelha o record mas por
 * linha.
 */
@Entity
@Table(name = "financial_installment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "recordId", "sequence", "dueDate", "status", "originalValue", "paidValue"})
public class FinancialInstallment {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "record_id", nullable = false, updatable = false)
  private UUID recordId;

  @Column(name = "sequence", nullable = false, updatable = false)
  private int sequence;

  @Column(name = "due_date", nullable = false)
  private LocalDate dueDate;

  @Column(name = "original_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal originalValue;

  @Column(name = "paid_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal paidValue;

  @Column(name = "fine_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal fineValue;

  @Column(name = "interest_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal interestValue;

  @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal discountValue;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private InstallmentStatus status;

  @Column(name = "bank_account_id")
  private UUID bankAccountId;

  @Column(name = "barcode")
  private String barcode;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public InstallmentId id() {
    return new InstallmentId(id);
  }

  public FinancialRecordId recordIdTyped() {
    return new FinancialRecordId(recordId);
  }

  public BankAccountId bankAccountIdTyped() {
    return bankAccountId == null ? null : new BankAccountId(bankAccountId);
  }

  public static FinancialInstallment create(
      UUID recordId,
      int sequence,
      LocalDate dueDate,
      BigDecimal originalValue,
      UUID bankAccountId,
      String barcode) {
    FinancialInstallment i = new FinancialInstallment();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.recordId = Objects.requireNonNull(recordId, "recordId");
    i.sequence = sequence;
    i.dueDate = Objects.requireNonNull(dueDate, "dueDate");
    i.originalValue = Objects.requireNonNull(originalValue, "originalValue");
    i.paidValue = BigDecimal.ZERO;
    i.fineValue = BigDecimal.ZERO;
    i.interestValue = BigDecimal.ZERO;
    i.discountValue = BigDecimal.ZERO;
    i.status = InstallmentStatus.OPEN;
    i.bankAccountId = bankAccountId;
    i.barcode = barcode;
    return i;
  }

  public void markCancelled() {
    this.status = InstallmentStatus.CANCELLED;
  }

  public void markRenegotiated() {
    this.status = InstallmentStatus.RENEGOTIATED;
  }

  /**
   * Aplica uma parcela de pagamento. Soma valores e recomputa status conforme {@code paidValue} vs
   * {@code originalValue}. Não valida overflow — caller (service) deve garantir {@code paidValue +
   * value <= originalValue + fineValue + interestValue} (regra v1: aceita exato).
   */
  public void applyPayment(
      java.math.BigDecimal value,
      java.math.BigDecimal fine,
      java.math.BigDecimal interest,
      java.math.BigDecimal discount) {
    this.paidValue = this.paidValue.add(value);
    this.fineValue = this.fineValue.add(fine == null ? java.math.BigDecimal.ZERO : fine);
    this.interestValue =
        this.interestValue.add(interest == null ? java.math.BigDecimal.ZERO : interest);
    this.discountValue =
        this.discountValue.add(discount == null ? java.math.BigDecimal.ZERO : discount);
    recomputeStatus();
  }

  /**
   * Reverte aplicação prévia de pagamento (estorno). Subtrai os mesmos valores. Recomputa status.
   * Caller garante que valores informados batem exatamente com uma aplicação existente.
   */
  public void revertPayment(
      java.math.BigDecimal value,
      java.math.BigDecimal fine,
      java.math.BigDecimal interest,
      java.math.BigDecimal discount) {
    this.paidValue = this.paidValue.subtract(value);
    this.fineValue = this.fineValue.subtract(fine == null ? java.math.BigDecimal.ZERO : fine);
    this.interestValue =
        this.interestValue.subtract(interest == null ? java.math.BigDecimal.ZERO : interest);
    this.discountValue =
        this.discountValue.subtract(discount == null ? java.math.BigDecimal.ZERO : discount);
    recomputeStatus();
  }

  private void recomputeStatus() {
    if (this.status == dev.dimi.erp.financeiro.InstallmentStatus.CANCELLED
        || this.status == dev.dimi.erp.financeiro.InstallmentStatus.RENEGOTIATED) {
      return; // estados terminais não mudam por pagamento.
    }
    int cmp = this.paidValue.compareTo(this.originalValue);
    if (cmp >= 0) {
      this.status = dev.dimi.erp.financeiro.InstallmentStatus.PAID;
    } else if (this.paidValue.signum() > 0) {
      this.status = dev.dimi.erp.financeiro.InstallmentStatus.PARTIALLY_PAID;
    } else {
      this.status = dev.dimi.erp.financeiro.InstallmentStatus.OPEN;
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
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FinancialInstallment other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
