package dev.dimi.erp.produto.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.produto.PriceTableId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Tabela de preço por filial e vigência. {@code branchId == null} significa tabela global do tenant
 * (aplica a todas as filiais). Resolução de preço (qual tabela vence quando há sobreposição) é
 * responsabilidade do {@code PriceLookupPort} no slice 4.2.
 */
@Entity
@Table(name = "price_table")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "name", "branchId", "validFrom", "validUntil", "active"})
public class PriceTable {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "branch_id")
  private UUID branchId;

  @Column(name = "valid_from", nullable = false)
  private LocalDate validFrom;

  @Column(name = "valid_until")
  private LocalDate validUntil;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public PriceTableId id() {
    return new PriceTableId(id);
  }

  public static PriceTable create(
      String name, UUID branchId, LocalDate validFrom, LocalDate validUntil) {
    Objects.requireNonNull(name, "name");
    Objects.requireNonNull(validFrom, "validFrom");
    if (validUntil != null && validUntil.isBefore(validFrom)) {
      throw new IllegalArgumentException("validUntil não pode ser anterior a validFrom");
    }
    PriceTable t = new PriceTable();
    t.id = UuidCreator.getTimeOrderedEpoch();
    t.name = name;
    t.branchId = branchId;
    t.validFrom = validFrom;
    t.validUntil = validUntil;
    t.active = true;
    return t;
  }

  public void update(String name, LocalDate validFrom, LocalDate validUntil) {
    if (name != null) this.name = name;
    if (validFrom != null) this.validFrom = validFrom;
    if (validUntil != null) this.validUntil = validUntil;
    if (this.validUntil != null && this.validUntil.isBefore(this.validFrom)) {
      throw new IllegalArgumentException("validUntil não pode ser anterior a validFrom");
    }
  }

  public void deactivate() {
    this.active = false;
  }

  public void reactivate() {
    this.active = true;
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
    if (!(o instanceof PriceTable other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
