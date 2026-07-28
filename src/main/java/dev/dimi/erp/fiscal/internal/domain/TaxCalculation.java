package dev.dimi.erp.fiscal.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Detalhamento por imposto (ICMS/IPI/PIS/COFINS/ISSQN/FCP/IBPT) na linha do documento.
 * Granularidade exigida por SPED + XML ACBr.
 */
@Entity
@Table(name = "tax_calculation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "invoiceItemId", "tax", "base", "rate", "value"})
public class TaxCalculation {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "invoice_item_id", nullable = false, updatable = false)
  private UUID invoiceItemId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tax", nullable = false, updatable = false)
  private TaxType tax;

  @Column(name = "cst_csosn", updatable = false)
  private String cstCsosn;

  @Column(name = "base", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal base;

  @Column(name = "rate", nullable = false, precision = 7, scale = 4, updatable = false)
  private BigDecimal rate;

  @Column(name = "value", nullable = false, precision = 15, scale = 2, updatable = false)
  private BigDecimal value;

  @Column(name = "base_st", precision = 15, scale = 2, updatable = false)
  private BigDecimal baseSt;

  @Column(name = "rate_st", precision = 7, scale = 4, updatable = false)
  private BigDecimal rateSt;

  @Column(name = "value_st", precision = 15, scale = 2, updatable = false)
  private BigDecimal valueSt;

  @Column(name = "mva", precision = 7, scale = 4, updatable = false)
  private BigDecimal mva;

  @Column(name = "benefit_code", updatable = false)
  private String benefitCode;

  @Column(name = "additional", updatable = false, columnDefinition = "jsonb")
  private String additional;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public TaxCalculationId id() {
    return new TaxCalculationId(id);
  }

  public InvoiceItemId invoiceItemIdTyped() {
    return new InvoiceItemId(invoiceItemId);
  }

  public static TaxCalculation create(
      UUID invoiceItemId,
      TaxType tax,
      String cstCsosn,
      BigDecimal base,
      BigDecimal rate,
      BigDecimal value,
      BigDecimal baseSt,
      BigDecimal rateSt,
      BigDecimal valueSt,
      BigDecimal mva,
      String benefitCode,
      String additional) {
    TaxCalculation t = new TaxCalculation();
    t.id = UuidCreator.getTimeOrderedEpoch();
    t.invoiceItemId = Objects.requireNonNull(invoiceItemId, "invoiceItemId");
    t.tax = Objects.requireNonNull(tax, "tax");
    t.cstCsosn = cstCsosn;
    t.base = Objects.requireNonNull(base, "base");
    t.rate = Objects.requireNonNull(rate, "rate");
    t.value = Objects.requireNonNull(value, "value");
    t.baseSt = baseSt;
    t.rateSt = rateSt;
    t.valueSt = valueSt;
    t.mva = mva;
    t.benefitCode = benefitCode;
    t.additional = additional;
    return t;
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
    if (!(o instanceof TaxCalculation other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
