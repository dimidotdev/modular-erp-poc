package dev.dimi.erp.cadastros.internal.domain;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonId;
import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Marca que uma pessoa desempenha uma modalidade (cliente, fornecedor, etc). Múltiplas modalidades
 * coexistem por pessoa (RN-CAD-005). {@code isActive=false} desativa o papel sem deletar a linha,
 * preservando histórico.
 */
@Entity
@Table(name = "person_modality")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "active"})
public class PersonModalityAssignment {

  @EmbeddedId private PersonModalityId id;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "activated_at", nullable = false)
  private Instant activatedAt;

  @Column(name = "deactivated_at")
  private Instant deactivatedAt;

  /** Cria uma nova atribuição ativa de modalidade. */
  public static PersonModalityAssignment activate(PersonId personId, Modality modality) {
    PersonModalityAssignment pma = new PersonModalityAssignment();
    pma.id = new PersonModalityId(personId.value(), modality);
    pma.active = true;
    pma.activatedAt = Instant.now();
    return pma;
  }

  /** Soft delete: marca como inativa, preservando o histórico (RN-CAD-005). */
  public void deactivate() {
    this.active = false;
    this.deactivatedAt = Instant.now();
  }

  /** Reativa uma atribuição que tinha sido desativada (limpa o {@code deactivatedAt}). */
  public void reactivate() {
    this.active = true;
    this.deactivatedAt = null;
    this.activatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PersonModalityAssignment other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
