package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.identidade.UserId;
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
 * Recebimento físico (gera INBOUND no estoque, RN-COM-012). Idempotência via {@code
 * idempotency_key} (RN-COM-014). {@code dfeDocumentId} fica como UUID cru até módulo fiscal.
 */
@Entity
@Table(name = "purchase_receipt")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "orderId", "number", "total"})
public class PurchaseReceipt {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "order_id", nullable = false, updatable = false)
  private UUID orderId;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "received_at", nullable = false)
  private Instant receivedAt;

  @Column(name = "supplier_invoice_key")
  private String supplierInvoiceKey;

  @Column(name = "supplier_invoice_number")
  private String supplierInvoiceNumber;

  @Column(name = "dfe_document_id")
  private UUID dfeDocumentId;

  @Column(name = "freight_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal freightValue;

  @Column(name = "total", nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "notes")
  private String notes;

  @Column(name = "user_id", nullable = false, updatable = false)
  private UUID userId;

  @Column(name = "idempotency_key", updatable = false)
  private String idempotencyKey;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public PurchaseReceiptId id() {
    return new PurchaseReceiptId(id);
  }

  public PurchaseOrderId orderIdTyped() {
    return new PurchaseOrderId(orderId);
  }

  public UserId userIdTyped() {
    return new UserId(userId);
  }

  public static PurchaseReceipt record(
      UUID orderId,
      long number,
      String supplierInvoiceKey,
      String supplierInvoiceNumber,
      UUID dfeDocumentId,
      BigDecimal freightValue,
      String notes,
      UUID userId,
      String idempotencyKey) {
    PurchaseReceipt r = new PurchaseReceipt();
    r.id = UuidCreator.getTimeOrderedEpoch();
    r.orderId = Objects.requireNonNull(orderId, "orderId");
    r.number = number;
    r.receivedAt = Instant.now();
    r.supplierInvoiceKey = supplierInvoiceKey;
    r.supplierInvoiceNumber = supplierInvoiceNumber;
    r.dfeDocumentId = dfeDocumentId;
    r.freightValue = freightValue == null ? BigDecimal.ZERO : freightValue;
    r.total = BigDecimal.ZERO;
    r.notes = notes;
    r.userId = Objects.requireNonNull(userId, "userId");
    r.idempotencyKey = idempotencyKey;
    return r;
  }

  public void updateTotal(BigDecimal total) {
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
    if (receivedAt == null) {
      receivedAt = now;
    }
    if (createdAt == null) {
      createdAt = now;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PurchaseReceipt other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
