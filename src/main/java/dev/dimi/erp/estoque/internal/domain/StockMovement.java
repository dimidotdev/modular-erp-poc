package dev.dimi.erp.estoque.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.Direction;
import dev.dimi.erp.estoque.MovementId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Registro imutável no ledger de movimentos (RN-EST-002). Não há setters — uma linha é criada via
 * factory e nunca alterada. Correção é por {@link Direction#REVERSAL} ou {@link
 * Direction#ADJUSTMENT}, gerando linha nova.
 *
 * <p>RN-EST-005: idempotência por {@code idempotency_key} (formato sugerido {@code
 * <origin_type>:<origin_id>:<sub_seq>}). Quando informado, índice parcial único impede duplicar.
 *
 * <p>RN-EST-008: TRANSFER usa par TRANSFER_OUT + TRANSFER_IN com mesmo {@code origin_id}
 * (transfer_id). Falha em qualquer um → rollback total.
 *
 * <p>RN-EST-009: REVERSAL aponta para movimento original em {@code reason} via prefixo {@code
 * REVERSAL_OF:<id>}.
 */
@Entity
@Table(name = "stock_movement")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "direction", "packagingId", "branchId", "quantity", "occurredAt"})
public class StockMovement {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "occurred_at", nullable = false, updatable = false)
  private Instant occurredAt;

  @Enumerated(EnumType.STRING)
  @Column(name = "direction", nullable = false, updatable = false)
  private Direction direction;

  @Column(name = "reason", nullable = false, updatable = false)
  private String reason;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "location_id", nullable = false, updatable = false)
  private UUID locationId;

  @Column(name = "batch_id", updatable = false)
  private UUID batchId;

  @Column(name = "quantity", nullable = false, updatable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_cost", updatable = false, precision = 15, scale = 6)
  private BigDecimal unitCost;

  @Column(name = "total_cost", updatable = false, precision = 15, scale = 6)
  private BigDecimal totalCost;

  @Column(name = "origin_type", updatable = false)
  private String originType;

  @Column(name = "origin_id", updatable = false)
  private UUID originId;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Column(name = "user_id", updatable = false)
  private UUID userId;

  @Column(name = "notes", updatable = false)
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public MovementId id() {
    return new MovementId(id);
  }

  public PackagingId packagingIdTyped() {
    return new PackagingId(packagingId);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public StockLocationId locationIdTyped() {
    return new StockLocationId(locationId);
  }

  public StockBatchId batchIdTyped() {
    return batchId == null ? null : new StockBatchId(batchId);
  }

  public UserId userIdTyped() {
    return userId == null ? null : new UserId(userId);
  }

  /**
   * Factory consolidada. Quantity sempre positiva; o {@link Direction} carrega o sentido. {@code
   * totalCost} é calculado automaticamente quando {@code unitCost} é informado.
   */
  public static StockMovement record(
      Direction direction,
      String reason,
      UUID packagingId,
      UUID branchId,
      UUID locationId,
      UUID batchId,
      BigDecimal quantity,
      BigDecimal unitCost,
      String originType,
      UUID originId,
      String idempotencyKey,
      UUID userId,
      String notes) {
    Objects.requireNonNull(direction, "direction");
    Objects.requireNonNull(reason, "reason");
    Objects.requireNonNull(packagingId, "packagingId");
    Objects.requireNonNull(branchId, "branchId");
    Objects.requireNonNull(locationId, "locationId");
    Objects.requireNonNull(quantity, "quantity");
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("quantity deve ser positiva (sentido vem de direction)");
    }
    if (unitCost != null && unitCost.signum() < 0) {
      throw new IllegalArgumentException("unitCost não pode ser negativo");
    }

    StockMovement m = new StockMovement();
    m.id = UuidCreator.getTimeOrderedEpoch();
    m.direction = direction;
    m.reason = reason;
    m.packagingId = packagingId;
    m.branchId = branchId;
    m.locationId = locationId;
    m.batchId = batchId;
    m.quantity = quantity;
    m.unitCost = unitCost;
    m.totalCost = unitCost == null ? null : quantity.multiply(unitCost);
    m.originType = originType;
    m.originId = originId;
    m.idempotencyKey = idempotencyKey;
    m.userId = userId;
    m.notes = notes;
    return m;
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
    if (!(o instanceof StockMovement other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
