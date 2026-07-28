package dev.dimi.erp.contratos.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.contratos.AmendmentId;
import dev.dimi.erp.contratos.AmendmentNotEditableException;
import dev.dimi.erp.contratos.AmendmentType;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.identidade.UserId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Aditivo contratual. Ciclo de vida: {@code DRAFT → EFFECTIVE} (efeito aplicado no contrato) ou
 * {@code DRAFT → CANCELLED} (descarte antes de efetivar). Após {@code EFFECTIVE} é imutável —
 * mudança requer novo aditivo.
 *
 * <p>{@code payload} fica como {@code String} em v1 para evitar dependência de Hibernate JsonType /
 * módulo jackson-jdbc; 10.x evolui para {@code @JdbcTypeCode(SqlTypes.JSON)} quando houver caso de
 * uso real além de "armazenar texto livre".
 */
@Entity
@Table(name = "contract_amendment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "contractId", "type", "status", "effectiveFrom"})
public class Amendment {

  /** Status do aditivo — discriminador {@code status} do banco. */
  public enum AmendmentStatus {
    DRAFT,
    EFFECTIVE,
    CANCELLED
  }

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "contract_id", nullable = false, updatable = false)
  private UUID contractId;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false)
  private AmendmentType type;

  @Setter
  @Column(name = "effective_from", nullable = false)
  private LocalDate effectiveFrom;

  @Setter
  @Column(name = "description", nullable = false)
  private String description;

  @Setter
  @Column(name = "new_end_date")
  private LocalDate newEndDate;

  @Setter
  @Column(name = "new_base_value", precision = 15, scale = 2)
  private BigDecimal newBaseValue;

  @Setter
  @Column(name = "payload")
  private String payload;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private AmendmentStatus status;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "effective_at")
  private Instant effectiveAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public AmendmentId typedId() {
    return new AmendmentId(id);
  }

  public ContractId contractIdTyped() {
    return new ContractId(contractId);
  }

  /** Cria aditivo em {@code DRAFT}. */
  public static Amendment draft(
      ContractId contractId,
      AmendmentType type,
      LocalDate effectiveFrom,
      String description,
      LocalDate newEndDate,
      BigDecimal newBaseValue,
      String payload,
      UserId createdBy) {
    Amendment a = new Amendment();
    a.id = UuidCreator.getTimeOrderedEpoch();
    a.contractId = Objects.requireNonNull(contractId, "contractId").value();
    a.type = Objects.requireNonNull(type, "type");
    a.effectiveFrom = Objects.requireNonNull(effectiveFrom, "effectiveFrom");
    a.description = Objects.requireNonNull(description, "description");
    a.newEndDate = newEndDate;
    a.newBaseValue = newBaseValue;
    a.payload = payload;
    a.status = AmendmentStatus.DRAFT;
    a.createdBy = Objects.requireNonNull(createdBy, "createdBy").value();
    return a;
  }

  /**
   * Efetiva o aditivo. Caller (service de application) é responsável por aplicar o efeito no
   * contrato ({@link Contract#updateBaseValue}, {@link Contract#setEndDate}, etc) na mesma
   * transação.
   */
  public void effectivate() {
    if (this.status != AmendmentStatus.DRAFT) {
      throw new AmendmentNotEditableException(typedId(), this.status.name());
    }
    this.status = AmendmentStatus.EFFECTIVE;
    this.effectiveAt = Instant.now();
  }

  /** Cancela aditivo ainda em {@code DRAFT}. Aditivo {@code EFFECTIVE} é imutável. */
  public void cancel() {
    if (this.status != AmendmentStatus.DRAFT) {
      throw new AmendmentNotEditableException(typedId(), this.status.name());
    }
    this.status = AmendmentStatus.CANCELLED;
    this.cancelledAt = Instant.now();
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
    if (!(o instanceof Amendment other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
