package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.financeiro.ChartAccountId;
import dev.dimi.erp.financeiro.CostCenterId;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Configurações por tenant (singleton: UNIQUE (singleton_key=true) garante 1 linha por schema).
 * Listeners de financeiro consultam esta tabela para preencher chart_account/cost_center default em
 * MVF criada a partir de SaleClosed/PurchaseReceived. Se setting ausente ou ID nulo, listener
 * publica {@code FinancialListenerSkippedEvent} em vez de criar MVF inválida.
 */
@Entity
@Table(name = "tenant_setting")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "defaultRevenueChartAccountId", "defaultExpenseChartAccountId"})
public class TenantSetting {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "singleton_key", nullable = false)
  private boolean singletonKey;

  @Column(name = "default_revenue_chart_account_id")
  private UUID defaultRevenueChartAccountId;

  @Column(name = "default_expense_chart_account_id")
  private UUID defaultExpenseChartAccountId;

  @Column(name = "default_cost_center_id")
  private UUID defaultCostCenterId;

  @Column(name = "updated_by")
  private UUID updatedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public TenantSettingId id() {
    return new TenantSettingId(id);
  }

  public ChartAccountId defaultRevenueChartAccountIdTyped() {
    return defaultRevenueChartAccountId == null
        ? null
        : new ChartAccountId(defaultRevenueChartAccountId);
  }

  public ChartAccountId defaultExpenseChartAccountIdTyped() {
    return defaultExpenseChartAccountId == null
        ? null
        : new ChartAccountId(defaultExpenseChartAccountId);
  }

  public CostCenterId defaultCostCenterIdTyped() {
    return defaultCostCenterId == null ? null : new CostCenterId(defaultCostCenterId);
  }

  public UserId updatedByTyped() {
    return updatedBy == null ? null : new UserId(updatedBy);
  }

  public static TenantSetting initial() {
    TenantSetting s = new TenantSetting();
    s.id = UuidCreator.getTimeOrderedEpoch();
    s.singletonKey = true;
    return s;
  }

  public void updateDefaults(
      UUID defaultRevenueChartAccountId,
      UUID defaultExpenseChartAccountId,
      UUID defaultCostCenterId,
      UUID updatedBy) {
    this.defaultRevenueChartAccountId = defaultRevenueChartAccountId;
    this.defaultExpenseChartAccountId = defaultExpenseChartAccountId;
    this.defaultCostCenterId = defaultCostCenterId;
    this.updatedBy = updatedBy;
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
    if (!(o instanceof TenantSetting other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
