package dev.dimi.erp.cadastros.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
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

/** Contato de uma pessoa (email, telefone, etc). Tipos catalogados via {@code address_type_id}. */
@Entity
@Table(name = "person_contact")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "personId", "label"})
public class PersonContact {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "person_id", nullable = false, updatable = false)
  private UUID personId;

  @Column(name = "contact_type_id")
  private UUID contactTypeId;

  @Column(name = "value", nullable = false)
  private String value;

  @Column(name = "label")
  private String label;

  @Column(name = "is_primary", nullable = false)
  private boolean primaryContact;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Cria novo contato associado a uma pessoa. */
  public static PersonContact create(
      UUID personId, UUID contactTypeId, String value, String label, boolean primaryContact) {
    PersonContact c = new PersonContact();
    c.id = UuidCreator.getTimeOrderedEpoch();
    c.personId = Objects.requireNonNull(personId, "personId");
    c.contactTypeId = contactTypeId;
    c.value = Objects.requireNonNull(value, "value");
    c.label = label;
    c.primaryContact = primaryContact;
    return c;
  }

  /** Aplica alterações parciais. Campos {@code null} preservam o valor atual. */
  public void update(UUID contactTypeId, String value, String label) {
    if (contactTypeId != null) this.contactTypeId = contactTypeId;
    if (value != null) this.value = value;
    if (label != null) this.label = label;
  }

  public void markAsPrimary() {
    this.primaryContact = true;
  }

  public void unmarkAsPrimary() {
    this.primaryContact = false;
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
    if (!(o instanceof PersonContact other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
