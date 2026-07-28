package dev.dimi.erp.identidade.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.identidade.RoleId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Role do tenant: conjunto nomeado de permissões.
 *
 * <p>System roles (criadas pelo {@code TenantProvisioningService} no bootstrap) têm {@code
 * isSystem=true} e não podem ser editadas/removidas. Tenants criam roles próprias com {@code
 * isSystem=false}.
 */
@Entity
@Table(name = "role")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "code"})
public class Role {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "code", nullable = false, unique = true)
  private String code;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "description")
  private String description;

  @Column(name = "is_system", nullable = false)
  private boolean system;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Factory method canônico. Use no provisioning ou em service de role custom. */
  public static Role create(String code, String displayName, String description, boolean system) {
    Role r = new Role();
    r.id = UuidCreator.getTimeOrderedEpoch();
    r.code = code;
    r.displayName = displayName;
    r.description = description;
    r.system = system;
    return r;
  }

  public RoleId id() {
    return new RoleId(id);
  }

  public void rename(String newDisplayName, String newDescription) {
    if (system) {
      throw new IllegalStateException("Role de sistema não pode ser renomeada: " + code);
    }
    this.displayName = newDisplayName;
    this.description = newDescription;
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
    if (!(o instanceof Role other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
