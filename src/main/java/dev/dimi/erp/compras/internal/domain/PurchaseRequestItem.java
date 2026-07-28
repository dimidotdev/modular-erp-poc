package dev.dimi.erp.compras.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.compras.PurchaseRequestId;
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
 * Linha de SC. {@code packagingId} é opcional — item livre (descrição-only) é permitido quando o
 * solicitante não conhece SKU exato; service do slice 6.2 resolve/cria SKU correspondente.
 */
@Entity
@Table(name = "purchase_request_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "requestId", "lineNumber", "description", "quantity"})
public class PurchaseRequestItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "request_id", nullable = false, updatable = false)
  private UUID requestId;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "packaging_id")
  private UUID packagingId;

  @Column(name = "description", nullable = false)
  private String description;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Column(name = "estimated_unit_cost", precision = 15, scale = 4)
  private BigDecimal estimatedUnitCost;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public PurchaseRequestItemId id() {
    return new PurchaseRequestItemId(id);
  }

  public PurchaseRequestId requestIdTyped() {
    return new PurchaseRequestId(requestId);
  }

  public PackagingId packagingIdTyped() {
    return packagingId == null ? null : new PackagingId(packagingId);
  }

  public static PurchaseRequestItem create(
      UUID requestId,
      int lineNumber,
      UUID packagingId,
      String description,
      BigDecimal quantity,
      BigDecimal estimatedUnitCost,
      String notes) {
    PurchaseRequestItem i = new PurchaseRequestItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.requestId = Objects.requireNonNull(requestId, "requestId");
    i.lineNumber = lineNumber;
    i.packagingId = packagingId;
    i.description = Objects.requireNonNull(description, "description");
    i.quantity = Objects.requireNonNull(quantity, "quantity");
    i.estimatedUnitCost = estimatedUnitCost;
    i.notes = notes;
    return i;
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
    if (!(o instanceof PurchaseRequestItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
