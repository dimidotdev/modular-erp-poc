package dev.dimi.erp.shared.modularization.internal;

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

@Entity
@Table(name = "tenant_module")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"code", "enabled"})
public class TenantModule {

  @Id
  @Column(name = "code", nullable = false, updatable = false)
  private String code;

  @Column(name = "enabled", nullable = false)
  private boolean enabled;

  @Column(name = "enabled_at")
  private Instant enabledAt;

  @Column(name = "enabled_by")
  private UUID enabledBy;

  @Column(name = "disabled_at")
  private Instant disabledAt;

  @Column(name = "disabled_by")
  private UUID disabledBy;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public static TenantModule provisioned(String code, boolean initiallyEnabled) {
    TenantModule m = new TenantModule();
    m.code = Objects.requireNonNull(code, "code");
    m.enabled = initiallyEnabled;
    if (initiallyEnabled) m.enabledAt = Instant.now();
    else m.disabledAt = Instant.now();
    return m;
  }

  public void enable(UUID by) {
    if (this.enabled) return;
    this.enabled = true;
    this.enabledAt = Instant.now();
    this.enabledBy = by;
    this.disabledAt = null;
    this.disabledBy = null;
  }

  public void disable(UUID by) {
    if (!this.enabled) return;
    this.enabled = false;
    this.disabledAt = Instant.now();
    this.disabledBy = by;
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (createdAt == null) createdAt = now;
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TenantModule other)) return false;
    return code != null && code.equals(other.code);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(code);
  }
}
