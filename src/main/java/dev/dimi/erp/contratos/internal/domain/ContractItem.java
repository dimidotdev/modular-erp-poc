package dev.dimi.erp.contratos.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.produto.PackagingId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Item recorrente do contrato. Discriminado por {@link ItemType}:
 *
 * <ul>
 *   <li>{@code PRODUCT}: vínculo com {@code packaging}; serviço fiscal NFe na geração.
 *   <li>{@code SERVICE}: {@code serviceCode} livre (LC 116); NFSe na geração.
 * </ul>
 *
 * Constraint {@code ck_contract_item_type_payload} no banco garante mutuamente exclusivos ({@code
 * PRODUCT} ⇒ {@code packagingId}, {@code SERVICE} ⇒ {@code serviceCode}).
 */
@Entity
@Table(name = "contract_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "contractId", "lineNumber", "itemType", "quantity", "netValue"})
public class ContractItem {

  /** Tipo de item — discriminador {@code item_type} do banco. */
  public enum ItemType {
    PRODUCT,
    SERVICE
  }

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "contract_id", nullable = false, updatable = false)
  private UUID contractId;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Enumerated(EnumType.STRING)
  @Column(name = "item_type", nullable = false, updatable = false)
  private ItemType itemType;

  @Column(name = "packaging_id")
  private UUID packagingId;

  @Column(name = "service_code")
  private String serviceCode;

  @Setter
  @Column(name = "description", nullable = false)
  private String description;

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

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public ContractId contractIdTyped() {
    return new ContractId(contractId);
  }

  public PackagingId packagingIdTyped() {
    return packagingId == null ? null : new PackagingId(packagingId);
  }

  /** Cria item de produto. {@code serviceCode} forçado nulo. */
  public static ContractItem product(
      ContractId contractId,
      int lineNumber,
      PackagingId packagingId,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue) {
    ContractItem it = new ContractItem();
    it.id = UuidCreator.getTimeOrderedEpoch();
    it.contractId = Objects.requireNonNull(contractId, "contractId").value();
    it.lineNumber = lineNumber;
    it.itemType = ItemType.PRODUCT;
    it.packagingId = Objects.requireNonNull(packagingId, "packagingId").value();
    it.serviceCode = null;
    it.description = Objects.requireNonNull(description, "description");
    it.quantity = Objects.requireNonNull(quantity, "quantity");
    it.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    it.discountValue = discountValue == null ? BigDecimal.ZERO : discountValue;
    it.netValue = computeNetValue(quantity, unitPrice, it.discountValue);
    return it;
  }

  /** Cria item de serviço. {@code packagingId} forçado nulo; {@code serviceCode} obrigatório. */
  public static ContractItem service(
      ContractId contractId,
      int lineNumber,
      String serviceCode,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue) {
    ContractItem it = new ContractItem();
    it.id = UuidCreator.getTimeOrderedEpoch();
    it.contractId = Objects.requireNonNull(contractId, "contractId").value();
    it.lineNumber = lineNumber;
    it.itemType = ItemType.SERVICE;
    it.packagingId = null;
    it.serviceCode = Objects.requireNonNull(serviceCode, "serviceCode");
    it.description = Objects.requireNonNull(description, "description");
    it.quantity = Objects.requireNonNull(quantity, "quantity");
    it.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    it.discountValue = discountValue == null ? BigDecimal.ZERO : discountValue;
    it.netValue = computeNetValue(quantity, unitPrice, it.discountValue);
    return it;
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
    if (!(o instanceof ContractItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
