package dev.dimi.erp.compras.internal.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Chave composta de {@code quotation_supplier}: (quotation_id, supplier_id). */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class QuotationSupplierKey implements Serializable {

  @Column(name = "quotation_id", nullable = false)
  private UUID quotationId;

  @Column(name = "supplier_id", nullable = false)
  private UUID supplierId;

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof QuotationSupplierKey other)) return false;
    return Objects.equals(quotationId, other.quotationId)
        && Objects.equals(supplierId, other.supplierId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(quotationId, supplierId);
  }
}
