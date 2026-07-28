package dev.dimi.erp.cadastros.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.PersonType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Pessoa física ou jurídica. Polimórfica via {@link PersonType}. Suporta múltiplas modalidades
 * (cliente, fornecedor, etc) via {@link PersonModalityAssignment} associada.
 *
 * <p>Esta classe é foundation do slice 3.1: persiste o estado. Comandos de domínio (registrar,
 * desativar, anonimizar) entram com os services do slice 3.2.
 */
@Entity
@Table(name = "person")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "type", "legalName"})
public class Person {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Enumerated(EnumType.STRING)
  @Column(name = "type", nullable = false, updatable = false)
  private PersonType type;

  @Column(name = "legal_name", nullable = false)
  private String legalName;

  @Column(name = "trade_name")
  private String tradeName;

  @Column(name = "cpf", length = 11)
  private String cpf;

  @Column(name = "cnpj", length = 14)
  private String cnpj;

  @Column(name = "rg")
  private String rg;

  @Column(name = "ie")
  private String ie;

  @Column(name = "im")
  private String im;

  @Column(name = "ie_exempt", nullable = false)
  private boolean ieExempt;

  @Column(name = "birth_date")
  private LocalDate birthDate;

  @Column(name = "foundation_date")
  private LocalDate foundationDate;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "deactivated_at")
  private Instant deactivatedAt;

  @Column(name = "anonymized", nullable = false)
  private boolean anonymized;

  @Column(name = "anonymized_at")
  private Instant anonymizedAt;

  @Column(name = "notes")
  private String notes;

  @Column(name = "segment_id")
  private UUID segmentId;

  @Column(name = "region_id")
  private UUID regionId;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public PersonId id() {
    return new PersonId(id);
  }

  // ----------------------------------------------------------------------------
  // Comportamento (slice 3.2)
  // ----------------------------------------------------------------------------

  /** Cria uma nova pessoa não-anonimizada, ativa. Documento (CPF/CNPJ) já validado pelo caller. */
  public static Person register(
      PersonType type,
      String legalName,
      String tradeName,
      String cpf,
      String cnpj,
      String rg,
      String ie,
      String im,
      boolean ieExempt,
      LocalDate birthDate,
      LocalDate foundationDate,
      String notes,
      UUID segmentId,
      UUID regionId) {
    Person p = new Person();
    p.id = UuidCreator.getTimeOrderedEpoch();
    p.type = Objects.requireNonNull(type, "type");
    p.legalName = Objects.requireNonNull(legalName, "legalName");
    p.tradeName = tradeName;
    p.cpf = cpf;
    p.cnpj = cnpj;
    p.rg = rg;
    p.ie = ie;
    p.im = im;
    p.ieExempt = ieExempt;
    p.birthDate = birthDate;
    p.foundationDate = foundationDate;
    p.notes = notes;
    p.segmentId = segmentId;
    p.regionId = regionId;
    p.active = true;
    p.anonymized = false;
    return p;
  }

  /**
   * Aplica alterações parciais. Campos {@code null} são preservados. Tipo (PF/PJ) e documento são
   * imutáveis (RN-CAD-003) e ficam de fora.
   */
  public void update(
      String legalName,
      String tradeName,
      String rg,
      String ie,
      String im,
      Boolean ieExempt,
      LocalDate birthDate,
      LocalDate foundationDate,
      String notes,
      UUID segmentId,
      UUID regionId) {
    if (legalName != null) this.legalName = legalName;
    if (tradeName != null) this.tradeName = tradeName;
    if (rg != null) this.rg = rg;
    if (ie != null) this.ie = ie;
    if (im != null) this.im = im;
    if (ieExempt != null) this.ieExempt = ieExempt;
    if (birthDate != null) this.birthDate = birthDate;
    if (foundationDate != null) this.foundationDate = foundationDate;
    if (notes != null) this.notes = notes;
    if (segmentId != null) this.segmentId = segmentId;
    if (regionId != null) this.regionId = regionId;
  }

  /**
   * Soft delete (RN-CAD-002). {@code reason} fica em campo {@code notes} appended para auditoria.
   */
  public void deactivate() {
    this.active = false;
    this.deactivatedAt = Instant.now();
  }

  public void reactivate() {
    this.active = true;
    this.deactivatedAt = null;
  }

  /**
   * Anonimiza dados pessoais identificáveis (RN-CAD-011/012). Operação irreversível: zera todos os
   * campos PII (legalName, tradeName, cpf/cnpj, rg, ie, im, birthDate, foundationDate, notes),
   * marca a entidade como inativa e seta {@code anonymized=true} + {@code anonymizedAt=now()}.
   *
   * <p>legalName é substituído por placeholder ("PESSOA ANONIMIZADA") para preservar referências
   * fiscais que mantêm FK para esta linha sem expor o nome real.
   */
  public void anonymize() {
    if (this.anonymized) {
      return;
    }
    this.legalName = "PESSOA ANONIMIZADA";
    this.tradeName = null;
    this.cpf = null;
    this.cnpj = null;
    this.rg = null;
    this.ie = null;
    this.im = null;
    this.birthDate = null;
    this.foundationDate = null;
    this.notes = null;
    this.active = false;
    this.deactivatedAt = Instant.now();
    this.anonymized = true;
    this.anonymizedAt = Instant.now();
  }

  // ----------------------------------------------------------------------------
  // Hooks JPA
  // ----------------------------------------------------------------------------

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
    if (!(o instanceof Person other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
