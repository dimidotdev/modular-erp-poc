package dev.dimi.erp.cadastros.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Base de todos os catálogos auxiliares de {@code cadastros} ({@code catalog_segment}, {@code
 * catalog_region}, etc). Todos compartilham as colunas {@code id/code/name/is_system/is_active/
 * timestamps}; payment_condition (fora desta hierarquia) adiciona campos próprios e fica para o
 * slice Financeiro.
 *
 * <p>Registros {@code system=true} são seedados via Flyway e não podem ser editados/deletados pelo
 * tenant (RN-CAD-013). Tenants criam entradas custom livremente.
 */
@MappedSuperclass
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "code", "name", "system", "active"})
public abstract class BaseCatalog {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  protected UUID id;

  @Column(name = "code", nullable = false)
  protected String code;

  @Column(name = "name", nullable = false)
  protected String name;

  @Column(name = "is_system", nullable = false)
  protected boolean system;

  @Column(name = "is_active", nullable = false)
  protected boolean active;

  @Column(name = "created_at", nullable = false, updatable = false)
  protected Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  protected Instant updatedAt;

  /** Inicializa um catálogo custom (system=false) ativo. {@code code} deve ser único na tabela. */
  protected void init(String code, String name) {
    this.id = UuidCreator.getTimeOrderedEpoch();
    this.code = Objects.requireNonNull(code, "code");
    this.name = Objects.requireNonNull(name, "name");
    this.system = false;
    this.active = true;
  }

  /** Renomeia. {@code code} é imutável para preservar referências cross-table por code. */
  public void rename(String name) {
    if (name != null && !name.isBlank()) {
      this.name = name;
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
    if (!(o instanceof BaseCatalog other)) return false;
    if (!o.getClass().equals(this.getClass())) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
