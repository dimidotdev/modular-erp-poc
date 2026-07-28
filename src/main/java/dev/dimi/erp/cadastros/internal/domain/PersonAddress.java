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

/** Endereço de uma pessoa. Único como padrão por pessoa (índice parcial; RN-CAD-004). */
@Entity
@Table(name = "person_address")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "personId", "city", "state"})
public class PersonAddress {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "person_id", nullable = false, updatable = false)
  private UUID personId;

  @Column(name = "address_type_id")
  private UUID addressTypeId;

  @Column(name = "cep", nullable = false, length = 8)
  private String cep;

  @Column(name = "street", nullable = false)
  private String street;

  @Column(name = "number")
  private String number;

  @Column(name = "complement")
  private String complement;

  @Column(name = "neighborhood")
  private String neighborhood;

  @Column(name = "city", nullable = false)
  private String city;

  @Column(name = "state", nullable = false, length = 2)
  private String state;

  @Column(name = "country", nullable = false, length = 2)
  private String country;

  @Column(name = "reference")
  private String reference;

  @Column(name = "is_default", nullable = false)
  private boolean defaultAddress;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  /** Cria novo endereço associado a uma pessoa. */
  public static PersonAddress create(
      UUID personId,
      UUID addressTypeId,
      String cep,
      String street,
      String number,
      String complement,
      String neighborhood,
      String city,
      String state,
      String country,
      String reference,
      boolean defaultAddress) {
    PersonAddress a = new PersonAddress();
    a.id = UuidCreator.getTimeOrderedEpoch();
    a.personId = Objects.requireNonNull(personId, "personId");
    a.addressTypeId = addressTypeId;
    a.cep = Objects.requireNonNull(cep, "cep");
    a.street = Objects.requireNonNull(street, "street");
    a.number = number;
    a.complement = complement;
    a.neighborhood = neighborhood;
    a.city = Objects.requireNonNull(city, "city");
    a.state = Objects.requireNonNull(state, "state");
    a.country = country == null ? "BR" : country;
    a.reference = reference;
    a.defaultAddress = defaultAddress;
    return a;
  }

  /** Aplica alterações parciais. Campos {@code null} preservam o valor atual. */
  public void update(
      UUID addressTypeId,
      String cep,
      String street,
      String number,
      String complement,
      String neighborhood,
      String city,
      String state,
      String country,
      String reference) {
    if (addressTypeId != null) this.addressTypeId = addressTypeId;
    if (cep != null) this.cep = cep;
    if (street != null) this.street = street;
    if (number != null) this.number = number;
    if (complement != null) this.complement = complement;
    if (neighborhood != null) this.neighborhood = neighborhood;
    if (city != null) this.city = city;
    if (state != null) this.state = state;
    if (country != null) this.country = country;
    if (reference != null) this.reference = reference;
  }

  public void markAsDefault() {
    this.defaultAddress = true;
  }

  public void unmarkAsDefault() {
    this.defaultAddress = false;
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
    if (country == null) {
      country = "BR";
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
    if (!(o instanceof PersonAddress other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
