package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
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
 * Resposta de um fornecedor para uma linha específica da SC. RN-COM-006: resposta pode omitir itens
 * (fornecedor não cota tudo) — modelado como ausência de registro, não null.
 */
@Entity
@Table(name = "quotation_response")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "quotationId", "supplierId", "requestItemLine", "quantity", "unitPrice"})
public class QuotationResponse {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "quotation_id", nullable = false, updatable = false)
  private UUID quotationId;

  @Column(name = "supplier_id", nullable = false, updatable = false)
  private UUID supplierId;

  @Column(name = "request_item_line", nullable = false, updatable = false)
  private int requestItemLine;

  @Column(name = "packaging_id")
  private UUID packagingId;

  @Column(name = "description", nullable = false)
  private String description;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Column(name = "unit_price", nullable = false, precision = 15, scale = 4)
  private BigDecimal unitPrice;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public QuotationResponseId id() {
    return new QuotationResponseId(id);
  }

  public QuotationId quotationIdTyped() {
    return new QuotationId(quotationId);
  }

  public PersonId supplierIdTyped() {
    return new PersonId(supplierId);
  }

  public PackagingId packagingIdTyped() {
    return packagingId == null ? null : new PackagingId(packagingId);
  }

  public static QuotationResponse create(
      UUID quotationId,
      UUID supplierId,
      int requestItemLine,
      UUID packagingId,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      String notes) {
    QuotationResponse r = new QuotationResponse();
    r.id = UuidCreator.getTimeOrderedEpoch();
    r.quotationId = Objects.requireNonNull(quotationId, "quotationId");
    r.supplierId = Objects.requireNonNull(supplierId, "supplierId");
    r.requestItemLine = requestItemLine;
    r.packagingId = packagingId;
    r.description = Objects.requireNonNull(description, "description");
    r.quantity = Objects.requireNonNull(quantity, "quantity");
    r.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    r.notes = notes;
    return r;
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
    if (!(o instanceof QuotationResponse other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
