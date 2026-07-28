package dev.dimi.erp.ordemservico.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.ordemservico.OsServiceId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Item de serviço prestado em uma OS. Espelha {@code service_order_service}.
 *
 * <p>{@code serviceCode} é o código LC 116/2003 do município (ex: {@code "14.01"} = manutenção de
 * equipamentos). {@code issqnRate} é a alíquota percentual ISSQN do município (0..5% típico) e
 * {@code issqnValue} é computado no {@link #create}.
 *
 * <p>{@code pisCofinsCalc} é {@code null} em v1 (slice 9.2a) — slice 9.2b vai persistir o cálculo
 * cumulativo (snapshot JSON) quando o billing puxar o serviço para uma NFSe.
 */
@Entity
@Table(name = "service_order_service")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "osId", "lineNumber", "serviceCode", "netValue", "issqnValue"})
public class OsService {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "os_id", nullable = false, updatable = false)
  private UUID osId;

  @Column(name = "line_number", nullable = false, updatable = false)
  private int lineNumber;

  @Setter
  @Column(name = "service_code", nullable = false)
  private String serviceCode;

  @Setter
  @Column(name = "description", nullable = false)
  private String description;

  @Setter
  @Column(name = "quantity", nullable = false, precision = 15, scale = 4)
  private BigDecimal quantity;

  @Setter
  @Column(name = "unit_price", nullable = false, precision = 15, scale = 4)
  private BigDecimal unitPrice;

  @Setter
  @Column(name = "discount_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal discountValue;

  @Setter
  @Column(name = "net_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal netValue;

  @Setter
  @Column(name = "issqn_rate", nullable = false, precision = 7, scale = 4)
  private BigDecimal issqnRate;

  @Setter
  @Column(name = "issqn_value", nullable = false, precision = 15, scale = 2)
  private BigDecimal issqnValue;

  /** Snapshot JSON do cálculo PIS/COFINS cumulativo — v1 sempre {@code null}; populado em 9.2b. */
  @Setter
  @Column(name = "pis_cofins_calc", columnDefinition = "jsonb")
  private String pisCofinsCalc;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public OsServiceId typedId() {
    return new OsServiceId(id);
  }

  public ServiceOrderId osIdTyped() {
    return new ServiceOrderId(osId);
  }

  /**
   * Cria nova linha de serviço. Computa {@code netValue = quantity*unitPrice - discount} e {@code
   * issqnValue = netValue * issqnRate / 100}, ambos scale=2 {@code HALF_UP}.
   */
  public static OsService create(
      ServiceOrderId osId,
      int lineNumber,
      String serviceCode,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue,
      BigDecimal issqnRate) {
    OsService s = new OsService();
    s.id = UuidCreator.getTimeOrderedEpoch();
    s.osId = Objects.requireNonNull(osId, "osId").value();
    s.lineNumber = lineNumber;
    s.serviceCode = Objects.requireNonNull(serviceCode, "serviceCode");
    s.description = Objects.requireNonNull(description, "description");
    s.quantity = quantity == null ? BigDecimal.ONE : quantity;
    s.unitPrice = Objects.requireNonNull(unitPrice, "unitPrice");
    s.discountValue = discountValue == null ? BigDecimal.ZERO : discountValue;
    s.issqnRate = issqnRate == null ? BigDecimal.ZERO : issqnRate;
    s.netValue = computeNetValue(s.quantity, s.unitPrice, s.discountValue);
    s.issqnValue = computeIssqn(s.netValue, s.issqnRate);
    s.pisCofinsCalc = null;
    return s;
  }

  private static BigDecimal computeNetValue(
      BigDecimal quantity, BigDecimal unitPrice, BigDecimal discountValue) {
    BigDecimal gross = quantity.multiply(unitPrice);
    return gross.subtract(discountValue).setScale(2, RoundingMode.HALF_UP);
  }

  private static BigDecimal computeIssqn(BigDecimal netValue, BigDecimal issqnRate) {
    return netValue.multiply(issqnRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
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
    if (!(o instanceof OsService other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
