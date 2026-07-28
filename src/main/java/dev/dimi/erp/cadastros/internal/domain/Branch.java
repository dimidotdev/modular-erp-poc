package dev.dimi.erp.cadastros.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Unidade operacional do tenant. Apenas uma pode ter {@code isMain=true} (RN-CAD-014, garantido por
 * índice parcial). Dados fiscais ficam em {@link BranchFiscal} 1:1.
 */
@Entity
@Table(name = "branch")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "code", "main"})
public class Branch {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "code", nullable = false)
  private String code;

  @Column(name = "name", nullable = false)
  private String name;

  @Column(name = "is_main", nullable = false)
  private boolean main;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "address_id")
  private UUID addressId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public BranchId id() {
    return new BranchId(id);
  }

  // ----------------------------------------------------------------------------
  // Comportamento (slice 3.3)
  // ----------------------------------------------------------------------------

  /**
   * Cria uma nova filial ativa. {@code addressId} é opcional (filial pode ser cadastrada antes de
   * possuir endereço próprio). Flag {@code isMain} é gerenciada pelo service para respeitar
   * RN-CAD-014.
   */
  public static Branch create(String code, String name, boolean isMain, UUID addressId) {
    Branch b = new Branch();
    b.id = UuidCreator.getTimeOrderedEpoch();
    b.code = Objects.requireNonNull(code, "code");
    b.name = Objects.requireNonNull(name, "name");
    b.main = isMain;
    b.active = true;
    b.addressId = addressId;
    return b;
  }

  /** Aplica alterações parciais. Campos {@code null} são preservados. {@code code} é imutável. */
  public void update(String name, UUID addressId) {
    if (name != null) this.name = name;
    if (addressId != null) this.addressId = addressId;
  }

  /** Soft delete da filial. RN-CAD-006 garante que fiscal recuse emissão por filial inativa. */
  public void deactivate() {
    this.active = false;
  }

  public void reactivate() {
    this.active = true;
  }

  /** Marca como matriz. Caller (service) responsável por desmarcar a anterior (RN-CAD-014). */
  public void markAsMain() {
    this.main = true;
  }

  public void unmarkAsMain() {
    this.main = false;
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
    if (!(o instanceof Branch other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
