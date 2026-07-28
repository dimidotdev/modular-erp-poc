package dev.dimi.erp.identidade.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.shared.multitenancy.TenantId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * Entidade que mapeia {@code shared.tenant}, o catálogo central de tenants da plataforma.
 *
 * <p>{@code @Table(schema = "shared", ...)} fixa o schema explicitamente — evita ambiguidade quando
 * o search_path do Hibernate aponta para o schema de um tenant ativo.
 */
@Entity
@Table(name = "tenant", schema = "shared")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "slug", "status"})
public class TenantEntity {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "slug", nullable = false, unique = true)
  private String slug;

  @Column(name = "schema_name", nullable = false, unique = true)
  private String schemaName;

  @Column(name = "display_name", nullable = false)
  private String displayName;

  @Column(name = "legal_name", nullable = false)
  private String legalName;

  @Column(name = "cnpj")
  private String cnpj;

  @Enumerated(EnumType.STRING)
  @Column(name = "status", nullable = false)
  private TenantStatus status;

  @Column(name = "plan", nullable = false)
  private String plan;

  @Column(name = "provisioned_at")
  private Instant provisionedAt;

  @Column(name = "suspended_at")
  private Instant suspendedAt;

  @Column(name = "terminated_at")
  private Instant terminatedAt;

  @Column(name = "failure_reason")
  private String failureReason;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Constrói um tenant em estado {@code PROVISIONING}. */
  public static TenantEntity beginProvisioning(
      String slug,
      String schemaName,
      String displayName,
      String legalName,
      String cnpj,
      String plan) {
    TenantEntity t = new TenantEntity();
    t.id = UuidCreator.getTimeOrderedEpoch();
    t.slug = Objects.requireNonNull(slug);
    t.schemaName = Objects.requireNonNull(schemaName);
    t.displayName = Objects.requireNonNull(displayName);
    t.legalName = Objects.requireNonNull(legalName);
    t.cnpj = cnpj;
    t.status = TenantStatus.PROVISIONING;
    t.plan = plan != null ? plan : "BASIC";
    return t;
  }

  public TenantId id() {
    return new TenantId(id);
  }

  public void markActive() {
    this.status = TenantStatus.ACTIVE;
    this.provisionedAt = Instant.now();
    this.failureReason = null;
  }

  public void markFailed(String reason) {
    this.status = TenantStatus.FAILED;
    this.failureReason = reason;
  }

  public void suspend() {
    if (status == TenantStatus.TERMINATED) {
      throw new IllegalStateException("tenant terminado não pode ser suspenso");
    }
    this.status = TenantStatus.SUSPENDED;
    this.suspendedAt = Instant.now();
  }

  public void reactivate() {
    if (status != TenantStatus.SUSPENDED) {
      throw new IllegalStateException("apenas tenant suspenso pode ser reativado");
    }
    this.status = TenantStatus.ACTIVE;
    this.suspendedAt = null;
  }

  public void terminate() {
    this.status = TenantStatus.TERMINATED;
    this.terminatedAt = Instant.now();
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
    if (!(o instanceof TenantEntity other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
