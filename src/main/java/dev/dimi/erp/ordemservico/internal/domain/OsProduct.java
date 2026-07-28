package dev.dimi.erp.ordemservico.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.ordemservico.OsProductAlreadyConsumedException;
import dev.dimi.erp.ordemservico.OsProductId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.produto.PackagingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Item de produto consumido em uma OS. Espelha {@code service_order_product}.
 *
 * <p>Ciclo de vida em duas etapas:
 *
 * <ol>
 *   <li><b>Adição</b> via {@link #create}: linha lançada na OS; estoque NÃO é tocado ainda.
 *   <li><b>Consumo</b> via {@link #markConsumed}: estoque OUTBOUND foi efetuado (módulo {@code
 *       estoque} retornou o {@code stockMovementId}); a linha passa a ter {@code consumedAt} e
 *       {@code stockMovementId} preenchidos. Idempotência protegida — segunda chamada lança {@link
 *       OsProductAlreadyConsumedException}.
 * </ol>
 *
 * <p>{@code os_id}, {@code line_number} e {@code packaging_id} são {@code updatable=false} — uma
 * vez lançada, a linha só muda em {@code consumedAt}/{@code stockMovementId}/totais.
 */
@Entity
@Table(name = "service_order_product")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "osId", "lineNumber", "packagingId", "quantity", "consumedAt"})
public class OsProduct {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "os_id", nullable = false, updatable = false)
  private UUID osId;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Setter
  @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Setter
  @Column(name = "unit_price", nullable = false, precision = 15, scale = 4)
  private BigDecimal unitPrice;

  @Setter
  @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal discountValue;

  @Setter
  @Column(name = "net_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal netValue;

  @Column(name = "consumed_at")
  private Instant consumedAt;

  @Column(name = "stock_movement_id")
  private UUID stockMovementId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public OsProductId typedId() {
    return new OsProductId(id);
  }

  public ServiceOrderId osIdTyped() {
    return new ServiceOrderId(osId);
  }

  public PackagingId packagingIdTyped() {
    return new PackagingId(packagingId);
  }

  /**
   * Cria nova linha de produto. {@code netValue} é computado como {@code quantity * unitPrice -
   * discount}, com scale=2 e {@code HALF_UP}.
   */
  public static OsProduct create(
      ServiceOrderId osId,
      int lineNumber,
      PackagingId packagingId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue) {
    OsProduct p = new OsProduct();
    p.id = UuidCreator.getTimeOrderedEpoch();
    p.osId = Objects.requireNonNull(osId, "osId").value();
    p.lineNumber = lineNumber;
    p.packagingId = Objects.requireNonNull(packagingId, "packagingId").value();
    p.quantity = Objects.requireNonNull(quantity, "quantity");
    p.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    p.discountValue = discountValue == null ? BigDecimal.ZERO : discountValue;
    p.netValue = computeNetValue(quantity, unitPrice, p.discountValue);
    return p;
  }

  /**
   * Marca a linha como consumida — estoque OUTBOUND já foi efetuado e produziu {@code
   * stockMovementId}. Idempotência: lança {@link OsProductAlreadyConsumedException} se já
   * consumida.
   */
  public void markConsumed(UUID stockMovementId) {
    if (this.consumedAt != null) {
      throw new OsProductAlreadyConsumedException(typedId());
    }
    this.consumedAt = Instant.now();
    this.stockMovementId = Objects.requireNonNull(stockMovementId, "stockMovementId");
  }

  private static BigDecimal computeNetValue(
      BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountValue) {
    BigDecimal gross = quantity.multiply(unitPrice);
    BigDecimal net = gross.subtract(discountValue);
    return net.setScale(2, RoundingMode.HALF_UP);
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
    if (!(o instanceof OsProduct other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
