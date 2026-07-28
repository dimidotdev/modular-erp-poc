package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.BankAccountType;
import dev.dimi.erp.financeiro.ChartAccountId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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
 * Conta bancária / caixa / cartão / gateway / voucher. Ligada a uma conta do plano de contas para
 * fluxo de caixa.
 */
@Entity
@Table(name = "bank_account")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "code", "name", "type"})
public class BankAccount {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false)
  private BankAccountType type;

  @Column(name = "bank_code")
  private String bankCode;

  @Column(name = "bank_agency")
  private String bankAgency;

  @Column(name = "bank_account_no")
  private String bankAccountNo;

  @Column(name = "chart_account_id", nullable = false)
  private UUID chartAccountId;

  @Column(name = "initial_balance", nullable = false, precision = 15, scale = 2)
  private BigDecimal initialBalance;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public BankAccountId id() {
    return new BankAccountId(id);
  }

  public ChartAccountId chartAccountIdTyped() {
    return new ChartAccountId(chartAccountId);
  }

  public static BankAccount create(
      String code,
      String name,
      BankAccountType type,
      UUID chartAccountId,
      BigDecimal initialBalance) {
    BankAccount a = new BankAccount();
    a.id = UuidCreator.getTimeOrderedEpoch();
    a.code = Objects.requireNonNull(code, "code");
    a.name = Objects.requireNonNull(name, "name");
    a.type = Objects.requireNonNull(type, "type");
    a.chartAccountId = Objects.requireNonNull(chartAccountId, "chartAccountId");
    a.initialBalance = Objects.requireNonNull(initialBalance, "initialBalance");
    a.isActive = true;
    return a;
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
    if (!(o instanceof BankAccount other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
