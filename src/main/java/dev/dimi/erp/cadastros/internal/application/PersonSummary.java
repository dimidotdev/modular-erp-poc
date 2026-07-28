package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.PersonType;
import dev.dimi.erp.cadastros.internal.domain.Person;
import dev.dimi.erp.cadastros.internal.domain.PersonModalityAssignment;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Projeção plana de {@link Person} para resposta de service. Carrega também as modalidades ativas
 * (para evitar lazy-loading em camadas web).
 */
public record PersonSummary(
    PersonId id,
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
    UUID regionId,
    boolean active,
    Instant deactivatedAt,
    boolean anonymized,
    List<Modality> activeModalities,
    Instant createdAt,
    Instant updatedAt) {

  public static PersonSummary of(Person p, Collection<PersonModalityAssignment> modalities) {
    List<Modality> active =
        modalities.stream()
            .filter(PersonModalityAssignment::isActive)
            .map(m -> m.getId().getModality())
            .sorted()
            .toList();
    return new PersonSummary(
        p.id(),
        p.getType(),
        p.getLegalName(),
        p.getTradeName(),
        p.getCpf(),
        p.getCnpj(),
        p.getRg(),
        p.getIe(),
        p.getIm(),
        p.isIeExempt(),
        p.getBirthDate(),
        p.getFoundationDate(),
        p.getNotes(),
        p.getSegmentId(),
        p.getRegionId(),
        p.isActive(),
        p.getDeactivatedAt(),
        p.isAnonymized(),
        active,
        p.getCreatedAt(),
        p.getUpdatedAt());
  }
}
