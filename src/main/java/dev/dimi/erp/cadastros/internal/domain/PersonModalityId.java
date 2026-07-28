package dev.dimi.erp.cadastros.internal.domain;

import dev.dimi.erp.cadastros.Modality;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

/** Chave composta de {@link PersonModalityAssignment}: {@code (person_id, modality)}. */
@Embeddable
public class PersonModalityId implements Serializable {

  @Column(name = "person_id", nullable = false, updatable = false)
  private UUID personId;

  @Enumerated(EnumType.STRING)
  @Column(name = "modality", nullable = false, updatable = false)
  private Modality modality;

  protected PersonModalityId() {
    // jpa
  }

  public PersonModalityId(UUID personId, Modality modality) {
    this.personId = Objects.requireNonNull(personId);
    this.modality = Objects.requireNonNull(modality);
  }

  public UUID getPersonId() {
    return personId;
  }

  public Modality getModality() {
    return modality;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof PersonModalityId other)) return false;
    return Objects.equals(personId, other.personId) && modality == other.modality;
  }

  @Override
  public int hashCode() {
    return Objects.hash(personId, modality);
  }
}
