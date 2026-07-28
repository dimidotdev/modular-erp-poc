package dev.dimi.erp.movimentacao.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.SalesOrderStatus;
import dev.dimi.erp.produto.PriceTableId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Pedido de venda — intenção sem efeito em estoque/financeiro (RN-MOV-001). Lifecycle gerenciado
 * via {@link SalesOrderStatus}; transições válidas + cálculo de totais + conversão entram nos
 * services do slice 5.2.
 *
 * <p>Foundation slice 5.1: persistência + factory mínimo. Itens são entidade separada ({@link
 * SalesOrderItem}) com FK back-reference.
 */
@Entity
@Table(name = "sales_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "branchId", "customerId", "number", "status", "total"})
public class SalesOrder {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "customer_id", nullable = false, updatable = false)
  private UUID customerId;

  @Column(name = "salesperson_id")
  private UUID salespersonId;

  @Column(name = "payment_condition_id")
  private UUID paymentConditionId;

  @Column(name = "price_table_id")
  private UUID priceTableId;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private SalesOrderStatus status;

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Column(name = "total_gross", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalGross;

  @Column(name = "total_discount", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalDiscount;

  @Column(name = "total", nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  @Column(name = "converted_at")
  private Instant convertedAt;

  @Column(name = "converted_sale_id")
  private UUID convertedSaleId;

  public SalesOrderId id() {
    return new SalesOrderId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public PersonId customerIdTyped() {
    return new PersonId(customerId);
  }

  public PersonId salespersonIdTyped() {
    return salespersonId == null ? null : new PersonId(salespersonId);
  }

  public PriceTableId priceTableIdTyped() {
    return priceTableId == null ? null : new PriceTableId(priceTableId);
  }

  public SaleId convertedSaleIdTyped() {
    return convertedSaleId == null ? null : new SaleId(convertedSaleId);
  }

  /**
   * Cria um pedido em rascunho (DRAFT). Totais inicializados em zero — recalculados ao adicionar
   * itens.
   */
  public static SalesOrder draft(
      UUID branchId,
      UUID customerId,
      UUID salespersonId,
      UUID paymentConditionId,
      UUID priceTableId,
      long number,
      LocalDate validUntil,
      String notes) {
    SalesOrder o = new SalesOrder();
    o.id = UuidCreator.getTimeOrderedEpoch();
    o.branchId = Objects.requireNonNull(branchId, "branchId");
    o.customerId = Objects.requireNonNull(customerId, "customerId");
    o.salespersonId = salespersonId;
    o.paymentConditionId = paymentConditionId;
    o.priceTableId = priceTableId;
    o.number = number;
    o.status = SalesOrderStatus.DRAFT;
    o.validUntil = validUntil;
    o.totalGross = BigDecimal.ZERO;
    o.totalDiscount = BigDecimal.ZERO;
    o.total = BigDecimal.ZERO;
    o.notes = notes;
    return o;
  }

  // --------------------------------------------------------------------------
  // Mutações de status — transições válidas no service (slice 5.2)
  // --------------------------------------------------------------------------

  public void submit() {
    this.status = SalesOrderStatus.PENDING_APPROVAL;
  }

  public void approve() {
    this.status = SalesOrderStatus.APPROVED;
  }

  public void reject() {
    this.status = SalesOrderStatus.REJECTED;
  }

  public void markConverted(UUID saleId) {
    this.status = SalesOrderStatus.CONVERTED;
    this.convertedAt = Instant.now();
    this.convertedSaleId = saleId;
  }

  public void cancel() {
    this.status = SalesOrderStatus.CANCELLED;
  }

  public void markExpired() {
    this.status = SalesOrderStatus.EXPIRED;
  }

  public void updateTotals(BigDecimal totalGross, BigDecimal totalDiscount, BigDecimal total) {
    if (totalGross != null) this.totalGross = totalGross;
    if (totalDiscount != null) this.totalDiscount = totalDiscount;
    if (total != null) this.total = total;
  }

  public void updateNotes(String notes) {
    if (notes != null) this.notes = notes;
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
    if (!(o instanceof SalesOrder other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
