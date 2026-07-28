package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.compras.PurchaseReceiptId;
import dev.dimi.erp.compras.SupplierReturnId;
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
 * Devolução a fornecedor (RN-COM-020). Referencia recebimento; gera OUTBOUND no estoque + estorno
 * proporcional de MVF a pagar + NFe de devolução de saída (slice fiscal). {@code invoiceId} fica
 * como UUID cru até módulo fiscal.
 */
@Entity
@Table(name = "supplier_return")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "receiptId", "number", "total"})
public class SupplierReturn {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "receipt_id", nullable = false, updatable = false)
  private UUID receiptId;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "issued_at", nullable = false)
  private Instant issuedAt;

  @Column(name = "reason", nullable = false)
  private String reason;

  @Column(name = "total", nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Column(name = "invoice_id")
  private UUID invoiceId;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public SupplierReturnId id() {
    return new SupplierReturnId(id);
  }

  public PurchaseReceiptId receiptIdTyped() {
    return new PurchaseReceiptId(receiptId);
  }

  public static SupplierReturn issue(UUID receiptId, long number, String reason, String notes) {
    SupplierReturn r = new SupplierReturn();
    r.id = UuidCreator.getTimeOrderedEpoch();
    r.receiptId = Objects.requireNonNull(receiptId, "receiptId");
    r.number = number;
    r.issuedAt = Instant.now();
    r.reason = Objects.requireNonNull(reason, "reason");
    r.total = BigDecimal.ZERO;
    r.notes = notes;
    return r;
  }

  public void updateTotal(BigDecimal total) {
    if (total != null) this.total = total;
  }

  public void attachInvoice(UUID invoiceId) {
    this.invoiceId = invoiceId;
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
    if (issuedAt == null) {
      issuedAt = now;
    }
    if (createdAt == null) {
      createdAt = now;
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof SupplierReturn other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
