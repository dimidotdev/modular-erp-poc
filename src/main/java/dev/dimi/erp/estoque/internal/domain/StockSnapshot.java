package dev.dimi.erp.estoque.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockSnapshotId;
import dev.dimi.erp.produto.PackagingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Saldo agregado de uma embalagem em uma combinação ({@code branch_id}, {@code location_id}, {@code
 * batch_id}). Materialização do ledger atualizada na mesma transação que insere o {@link
 * StockMovement} (RN-EST-003); leitura pública via {@code StockQueryPort} no slice 4.4.
 *
 * <p>Foundation: estado + factories e métodos básicos de mutação do saldo. WAC + lock pessimista +
 * regras de transferência atômica entram nos services do slice 4.4.
 *
 * <p>{@code quantityOnHand} sempre {@code >=} {@code quantityReserved} (CHECK DDL). Tentativa de
 * mutação que violaria essa invariante deve lançar antes de tocar o banco; backstop fica no DDL.
 */
@Entity
@Table(name = "stock_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(
    of = {"id", "packagingId", "branchId", "locationId", "quantityOnHand", "quantityReserved"})
public class StockSnapshot {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "location_id", nullable = false, updatable = false)
  private UUID locationId;

  @Column(name = "batch_id", updatable = false)
  private UUID batchId;

  @Column(name = "quantity_on_hand", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantityOnHand;

  @Column(name = "quantity_reserved", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantityReserved;

  @Column(name = "weighted_avg_cost", nullable = false, precision = 15, scale = 6)
  private BigDecimal weightedAvgCost;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public StockSnapshotId id() {
    return new StockSnapshotId(id);
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

  /** Saldo disponível para venda (RN-EST-007): {@code quantity_on_hand - quantity_reserved}. */
  public BigDecimal quantityAvailable() {
    return quantityOnHand.subtract(quantityReserved);
  }

  /**
   * Cria uma nova linha de snapshot zerada. Slice 4.4 populará via service ao processar primeiro
   * movement em uma tupla nova.
   */
  public static StockSnapshot zeroed(
      UUID packagingId, UUID branchId, UUID locationId, UUID batchId) {
    StockSnapshot s = new StockSnapshot();
    s.id = UuidCreator.getTimeOrderedEpoch();
    s.packagingId = Objects.requireNonNull(packagingId, "packagingId");
    s.branchId = Objects.requireNonNull(branchId, "branchId");
    s.locationId = Objects.requireNonNull(locationId, "locationId");
    s.batchId = batchId;
    s.quantityOnHand = BigDecimal.ZERO;
    s.quantityReserved = BigDecimal.ZERO;
    s.weightedAvgCost = BigDecimal.ZERO;
    return s;
  }

  /**
   * Aplica entrada de quantidade com possível recálculo de WAC. RN-EST-014: WAC armazenado em 6
   * casas. Fórmula RN-EST-001:
   *
   * <pre>
   * new_wac = ((qtd_atual × wac_atual) + (qtd_entrada × custo_entrada)) / (qtd_atual + qtd_entrada)
   * </pre>
   *
   * Quando {@code unitCost} é {@code null} (ex: estorno de saída), apenas a quantidade muda — WAC
   * preservado.
   */
  public void applyInbound(BigDecimal quantity, BigDecimal unitCost) {
    Objects.requireNonNull(quantity, "quantity");
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("quantity deve ser positiva");
    }
    BigDecimal newOnHand = this.quantityOnHand.add(quantity);
    if (unitCost != null && unitCost.signum() >= 0) {
      BigDecimal currentValue = this.quantityOnHand.multiply(this.weightedAvgCost);
      BigDecimal incomingValue = quantity.multiply(unitCost);
      this.weightedAvgCost =
          currentValue.add(incomingValue).divide(newOnHand, 6, java.math.RoundingMode.HALF_UP);
    }
    this.quantityOnHand = newOnHand;
  }

  /**
   * Saída de quantidade. RN-EST-006: estoque negativo só com config explícita. Aqui a verificação
   * básica garante {@code quantityOnHand - quantity >= quantityReserved} (não consumir reserva
   * acidentalmente); regra do "allow_negative_stock" fica para o service no slice 4.4.
   */
  public void applyOutbound(BigDecimal quantity) {
    Objects.requireNonNull(quantity, "quantity");
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("quantity deve ser positiva");
    }
    BigDecimal newOnHand = this.quantityOnHand.subtract(quantity);
    if (newOnHand.compareTo(this.quantityReserved) < 0) {
      throw new IllegalStateException("saída deixaria saldo abaixo do reservado");
    }
    this.quantityOnHand = newOnHand;
  }

  /** RN-EST-007: reserva é "número virtual" — incrementa reserva sem mexer em on-hand. */
  public void reserve(BigDecimal quantity) {
    Objects.requireNonNull(quantity, "quantity");
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("quantity deve ser positiva");
    }
    BigDecimal newReserved = this.quantityReserved.add(quantity);
    if (newReserved.compareTo(this.quantityOnHand) > 0) {
      throw new IllegalStateException("reserva excederia o saldo em mão");
    }
    this.quantityReserved = newReserved;
  }

  /** Libera reserva sem efetivar saída — RN-EST-007 caminho de cancelamento de pedido. */
  public void releaseReservation(BigDecimal quantity) {
    Objects.requireNonNull(quantity, "quantity");
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("quantity deve ser positiva");
    }
    BigDecimal newReserved = this.quantityReserved.subtract(quantity);
    if (newReserved.signum() < 0) {
      throw new IllegalStateException("liberação maior que o reservado");
    }
    this.quantityReserved = newReserved;
  }

  /**
   * Efetiva uma reserva: consome reserva E saldo em mão simultaneamente. Usado quando uma venda em
   * aberto fecha.
   */
  public void shipReservation(BigDecimal quantity) {
    Objects.requireNonNull(quantity, "quantity");
    if (quantity.signum() <= 0) {
      throw new IllegalArgumentException("quantity deve ser positiva");
    }
    if (quantity.compareTo(this.quantityReserved) > 0) {
      throw new IllegalStateException("ship excede a reserva existente");
    }
    this.quantityReserved = this.quantityReserved.subtract(quantity);
    this.quantityOnHand = this.quantityOnHand.subtract(quantity);
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
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
    if (!(o instanceof StockSnapshot other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
