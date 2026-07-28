package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.produto.PackagingId;
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
 * Linha de {@link Sale}. CFOP preenchido quando NFe é emitida (módulo fiscal/slice 8). Em sale
 * fechada, todos campos são efetivamente imutáveis — modificação só via cancelar + nova venda
 * (RN-MOV-008).
 */
@Entity
@Table(name = "sale_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "saleId", "lineNumber", "packagingId", "quantity", "netValue"})
public class SaleItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "sale_id", nullable = false, updatable = false)
  private UUID saleId;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "packaging_id", nullable = false, updatable = false)
  private UUID packagingId;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false, precision = 15, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal discountValue;

  @Column(name = "net_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal netValue;

  @Column(name = "cfop", length = 4)
  private String cfop;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public SaleItemId id() {
    return new SaleItemId(id);
  }

  public SaleId saleIdTyped() {
    return new SaleId(saleId);
  }

  public PackagingId packagingIdTyped() {
    return new PackagingId(packagingId);
  }

  public static SaleItem create(
      UUID saleId,
      int lineNumber,
      UUID packagingId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue,
      BigDecimal netValue,
      String cfop) {
    SaleItem i = new SaleItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.saleId = Objects.requireNonNull(saleId, "saleId");
    i.lineNumber = lineNumber;
    i.packagingId = Objects.requireNonNull(packagingId, "packagingId");
    i.quantity = Objects.requireNonNull(quantity, "quantity");
    i.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    i.discountValue = discountValue == null ? BigDecimal.ZERO : discountValue;
    i.netValue = Objects.requireNonNull(netValue, "netValue");
    i.cfop = cfop;
    return i;
  }

  public void update(
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue,
      BigDecimal netValue,
      String cfop) {
    if (quantity != null) this.quantity = quantity;
    if (unitPrice != null) this.unitPrice = unitPrice;
    if (discountValue != null) this.discountValue = discountValue;
    if (netValue != null) this.netValue = netValue;
    if (cfop != null) this.cfop = cfop;
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
    if (!(o instanceof SaleItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
