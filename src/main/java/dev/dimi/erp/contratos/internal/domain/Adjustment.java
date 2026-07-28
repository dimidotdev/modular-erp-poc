package dev.dimi.erp.contratos.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.contratos.AdjustmentId;
import dev.dimi.erp.contratos.AdjustmentIndex;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Reajuste de valor base por índice (IGPM/IPCA/INPC/MANUAL). Registro append-only para auditoria
 * histórica — não tem ciclo de vida nem updates. Efeito é aplicado em {@code Contract.baseValue}
 * pelo service de application via {@link Contract#applyAdjustment(BigDecimal, LocalDate)} na mesma
 * transação.
 *
 * <p>UNIQUE {@code (contract_id, applied_on)} no banco impede dois reajustes na mesma data.
 */
@Entity
@Table(name = "contract_adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "contractId", "appliedOn", "indexKind", "percentage"})
public class Adjustment {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "contract_id", nullable = false, updatable = false)
  private UUID contractId;

  @Column(name = "applied_on", nullable = false, updatable = false)
  private LocalDate appliedOn;

  @Enumerated(EnumType.STRING)
  @Column(name = "index_kind", nullable = false, updatable = false)
  private AdjustmentIndex indexKind;

  @Column(name = "percentage", nullable = false, updatable = false, precision = 7, scale = 4)
  private BigDecimal percentage;

  @Column(name = "old_base_value", nullable = false, updatable = false, precision = 15, scale = 2)
  private BigDecimal oldBaseValue;

  @Column(name = "new_base_value", nullable = false, updatable = false, precision = 15, scale = 2)
  private BigDecimal newBaseValue;

  @Column(name = "notes", updatable = false)
  private String notes;

  @Column(name = "applied_by", nullable = false, updatable = false)
  private UUID appliedBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public AdjustmentId typedId() {
    return new AdjustmentId(id);
  }

  public ContractId contractIdTyped() {
    return new ContractId(contractId);
  }

  /**
   * Registra reajuste aplicado. Computa {@code newBaseValue = oldBaseValue * (1 + percentage/100)}
   * com scale=2 {@code HALF_UP} — mesma fórmula de {@link Contract#applyAdjustment} (caller deve
   * garantir consistência invocando os dois em sequência).
   */
  public static Adjustment apply(
      ContractId contractId,
      LocalDate appliedOn,
      AdjustmentIndex indexKind,
      BigDecimal percentage,
      BigDecimal oldBaseValue,
      String notes,
      UserId appliedBy) {
    Adjustment a = new Adjustment();
    a.id = UuidCreator.getTimeOrderedEpoch();
    a.contractId = Objects.requireNonNull(contractId, "contractId").value();
    a.appliedOn = Objects.requireNonNull(appliedOn, "appliedOn");
    a.indexKind = Objects.requireNonNull(indexKind, "indexKind");
    a.percentage = Objects.requireNonNull(percentage, "percentage");
    a.oldBaseValue = Objects.requireNonNull(oldBaseValue, "oldBaseValue");
    BigDecimal factor =
        BigDecimal.ONE.add(percentage.divide(new BigDecimal("100"), 10, RoundingMode.HALF_UP));
    a.newBaseValue = oldBaseValue.multiply(factor).setScale(2, RoundingMode.HALF_UP);
    a.notes = notes;
    a.appliedBy = Objects.requireNonNull(appliedBy, "appliedBy").value();
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
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Adjustment other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
