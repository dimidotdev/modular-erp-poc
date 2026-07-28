package dev.dimi.erp.compras.internal.domain;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.QuotationSupplierStatus;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Fornecedor convidado numa cotação. PK composta {@code (quotation_id, supplier_id)} — {@link
 * QuotationSupplierKey}.
 */
@Entity
@Table(name = "quotation_supplier")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"key", "status"})
public class QuotationSupplier {

  @EmbeddedId private QuotationSupplierKey key;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private QuotationSupplierStatus status;

  @Column(name = "invited_at", nullable = false)
  private Instant invitedAt;

  @Column(name = "responded_at")
  private Instant respondedAt;

  @Column(name = "payment_condition_id")
  private UUID paymentConditionId;

  @Column(name = "delivery_days")
  private Integer deliveryDays;

  @Column(name = "freight_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal freightValue;

  @Column(name = "notes")
  private String notes;

  public QuotationId quotationIdTyped() {
    return new QuotationId(key.getQuotationId());
  }

  public PersonId supplierIdTyped() {
    return new PersonId(key.getSupplierId());
  }

  public static QuotationSupplier invite(
      UUID quotationId,
      UUID supplierId,
      UUID paymentConditionId,
      Integer deliveryDays,
      BigDecimal freightValue,
      String notes) {
    QuotationSupplier s = new QuotationSupplier();
    s.key =
        new QuotationSupplierKey(
            Objects.requireNonNull(quotationId, "quotationId"),
            Objects.requireNonNull(supplierId, "supplierId"));
    s.status = QuotationSupplierStatus.INVITED;
    s.invitedAt = Instant.now();
    s.paymentConditionId = paymentConditionId;
    s.deliveryDays = deliveryDays;
    s.freightValue = freightValue == null ? BigDecimal.ZERO : freightValue;
    s.notes = notes;
    return s;
  }

  public void markResponded() {
    this.status = QuotationSupplierStatus.RESPONDED;
    this.respondedAt = Instant.now();
  }

  public void markDeclined() {
    this.status = QuotationSupplierStatus.DECLINED;
    this.respondedAt = Instant.now();
  }

  public void markExpired() {
    this.status = QuotationSupplierStatus.EXPIRED;
  }

  public void updateConditions(
      UUID paymentConditionId, Integer deliveryDays, BigDecimal freightValue, String notes) {
    if (paymentConditionId != null) this.paymentConditionId = paymentConditionId;
    if (deliveryDays != null) this.deliveryDays = deliveryDays;
    if (freightValue != null) this.freightValue = freightValue;
    if (notes != null) this.notes = notes;
  }

  @PrePersist
  void onPersist() {
    if (invitedAt == null) {
      invitedAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof QuotationSupplier other)) return false;
    return key != null && key.equals(other.key);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(key);
  }
}
