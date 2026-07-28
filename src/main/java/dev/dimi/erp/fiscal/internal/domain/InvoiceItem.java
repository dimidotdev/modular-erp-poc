package dev.dimi.erp.fiscal.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.fiscal.InvoiceId;
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
 * Linha do documento fiscal com snapshot do SKU (NCM, descrição). RN-FIS-009: imutável após
 * autorização — campos {@code updatable=false} bloqueiam alterações no nível JPA.
 */
@Entity
@Table(name = "invoice_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "invoiceId", "lineNumber", "itemDescription", "quantity", "netValue"})
public class InvoiceItem {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "invoice_id", nullable = false, updatable = false)
  private UUID invoiceId;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Column(name = "packaging_id", updatable = false)
  private UUID packagingId;

  @Column(name = "item_code", updatable = false)
  private String itemCode;

  @Column(name = "item_description", nullable = false, updatable = false)
  private String itemDescription;

  @Column(name = "ncm", updatable = false)
  private String ncm;

  @Column(name = "cest", updatable = false)
  private String cest;

  @Column(name = "cfop", nullable = false, updatable = false)
  private String cfop;

  @Column(name = "origin", nullable = false, updatable = false)
  private String origin;

  @Column(name = "quantity", nullable = false, precision = 15, scale = 4, updatable = false)
  private BigDecimal quantity;

  @Column(name = "unit_commercial", nullable = false, updatable = false)
  private String unitCommercial;

  @Column(name = "unit_fiscal", nullable = false, updatable = false)
  private String unitFiscal;

  @Column(name = "unit_value", nullable = false, precision = 15, scale = 4, updatable = false)
  private BigDecimal unitValue;

  @Column(name = "gross_value", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal grossValue;

  @Column(name = "discount", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal discount;

  @Column(name = "net_value", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal netValue;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public InvoiceItemId id() {
    return new InvoiceItemId(id);
  }

  public InvoiceId invoiceIdTyped() {
    return new InvoiceId(invoiceId);
  }

  public PackagingId packagingIdTyped() {
    return packagingId == null ? null : new PackagingId(packagingId);
  }

  public static InvoiceItem create(
      UUID invoiceId,
      int lineNumber,
      UUID packagingId,
      String itemCode,
      String itemDescription,
      String ncm,
      String cest,
      String cfop,
      String origin,
      BigDecimal quantity,
      String unitCommercial,
      String unitFiscal,
      BigDecimal unitValue,
      BigDecimal grossValue,
      BigDecimal discount,
      BigDecimal netValue) {
    InvoiceItem i = new InvoiceItem();
    i.id = UuidCreator.getTimeOrderedEpoch();
    i.invoiceId = Objects.requireNonNull(invoiceId, "invoiceId");
    i.lineNumber = lineNumber;
    i.packagingId = packagingId;
    i.itemCode = itemCode;
    i.itemDescription = Objects.requireNonNull(itemDescription, "itemDescription");
    i.ncm = ncm;
    i.cest = cest;
    i.cfop = Objects.requireNonNull(cfop, "cfop");
    i.origin = Objects.requireNonNull(origin, "origin");
    i.quantity = Objects.requireNonNull(quantity, "quantity");
    i.unitCommercial = Objects.requireNonNull(unitCommercial, "unitCommercial");
    i.unitFiscal = Objects.requireNonNull(unitFiscal, "unitFiscal");
    i.unitValue = Objects.requireNonNull(unitValue, "unitValue");
    i.grossValue = Objects.requireNonNull(grossValue, "grossValue");
    i.discount = Objects.requireNonNull(discount, "discount");
    i.netValue = Objects.requireNonNull(netValue, "netValue");
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
    if (!(o instanceof InvoiceItem other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
