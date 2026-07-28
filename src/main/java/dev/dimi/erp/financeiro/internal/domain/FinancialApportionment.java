package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.financeiro.FinancialRecordId;
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
 * Rateio da MVF entre conta contábil e centro de custo. Soma dos rateios = total da MVF (validado
 * no service slice 7.2).
 */
@Entity
@Table(name = "financial_apportionment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "recordId", "chartAccountId", "costCenterId", "value"})
public class FinancialApportionment {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "record_id", nullable = false, updatable = false)
  private UUID recordId;

  @Column(name = "chart_account_id", nullable = false, updatable = false)
  private UUID chartAccountId;

  @Column(name = "cost_center_id", updatable = false)
  private UUID costCenterId;

  @Column(name = "value", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal value;

  @Column(name = "percentage", precision = 5, scale = 4, updatable = false)
  private BigDecimal percentage;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public ApportionmentId id() {
    return new ApportionmentId(id);
  }

  public FinancialRecordId recordIdTyped() {
    return new FinancialRecordId(recordId);
  }

  public ChartAccountId chartAccountIdTyped() {
    return new ChartAccountId(chartAccountId);
  }

  public CostCenterId costCenterIdTyped() {
    return costCenterId == null ? null : new CostCenterId(costCenterId);
  }

  public static FinancialApportionment create(
      UUID recordId,
      UUID chartAccountId,
      UUID costCenterId,
      BigDecimal value,
      BigDecimal percentage,
      String notes) {
    FinancialApportionment a = new FinancialApportionment();
    a.id = UuidCreator.getTimeOrderedEpoch();
    a.recordId = Objects.requireNonNull(recordId, "recordId");
    a.chartAccountId = Objects.requireNonNull(chartAccountId, "chartAccountId");
    a.costCenterId = costCenterId;
    a.value = Objects.requireNonNull(value, "value");
    a.percentage = percentage;
    a.notes = notes;
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
    if (!(o instanceof FinancialApportionment other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
