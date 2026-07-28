package dev.dimi.erp.auditoria.internal.domain;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.RetentionAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Política de retenção por {@link AuditCategory}. Espelha a tabela {@code audit.retention_policy}
 * (V9 global), seeded por categoria.
 *
 * <p>Linha por categoria — o job de retenção lê a política, aplica {@link #retainDays} para
 * encontrar a janela de corte e executa {@link #actionAtEol} (PURGE ou ANONYMIZE). Não é criada nem
 * removida em runtime; apenas {@link #update(int, RetentionAction)}.
 */
@Entity
@Table(name = "retention_policy", schema = "audit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"category", "retainDays", "actionAtEol"})
public class RetentionPolicy {

  @Id
  @Enumerated(EnumType.STRING)
  @Column(name = "category", nullable = false, updatable = false)
  private AuditCategory category;

  @Column(name = "retain_days", nullable = false)
  private int retainDays;

  @Enumerated(EnumType.STRING)
  @Column(name = "action_at_eol", nullable = false)
  private RetentionAction actionAtEol;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public void update(int retainDays, RetentionAction action) {
    if (retainDays <= 0) {
      throw new IllegalArgumentException("retainDays must be positive");
    }
    this.retainDays = retainDays;
    this.actionAtEol = Objects.requireNonNull(action);
    this.updatedAt = Instant.now();
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof RetentionPolicy other)) return false;
    return category != null && category == other.category;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(category);
  }
}
