package dev.dimi.erp.financeiro.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Congelamento contábil de período. {@code branchId} nulo = todas as filiais. Step-up obrigatório
 * para criar/remover (slice 7.6).
 */
@Entity
@Table(name = "frozen_period")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "branchId", "periodStart", "periodEnd"})
public class FrozenPeriod {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "branch_id", updatable = false)
  private UUID branchId;

  @Column(name = "period_start", nullable = false, updatable = false)
  private LocalDate periodStart;

  @Column(name = "period_end", nullable = false, updatable = false)
  private LocalDate periodEnd;

  @Column(name = "frozen_at", nullable = false, updatable = false)
  private Instant frozenAt;

  @Column(name = "frozen_by", nullable = false, updatable = false)
  private UUID frozenBy;

  @Column(name = "reason", nullable = false, updatable = false)
  private String reason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public FrozenPeriodId id() {
    return new FrozenPeriodId(id);
  }

  public BranchId branchIdTyped() {
    return branchId == null ? null : new BranchId(branchId);
  }

  public UserId frozenByTyped() {
    return new UserId(frozenBy);
  }

  public static FrozenPeriod freeze(
      UUID branchId, LocalDate periodStart, LocalDate periodEnd, UUID frozenBy, String reason) {
    FrozenPeriod p = new FrozenPeriod();
    p.id = UuidCreator.getTimeOrderedEpoch();
    p.branchId = branchId;
    p.periodStart = Objects.requireNonNull(periodStart, "periodStart");
    p.periodEnd = Objects.requireNonNull(periodEnd, "periodEnd");
    p.frozenBy = Objects.requireNonNull(frozenBy, "frozenBy");
    p.reason = Objects.requireNonNull(reason, "reason");
    p.frozenAt = Instant.now();
    return p;
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (frozenAt == null) {
      frozenAt = now;
    }
    if (createdAt == null) {
      createdAt = now;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof FrozenPeriod other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
