package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.PersonType;
import dev.dimi.erp.cadastros.internal.application.PersonSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Resposta HTTP plana para um {@link PersonSummary}. */
public record PersonApiResponse(
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

  public static PersonApiResponse from(PersonSummary s) {
    return new PersonApiResponse(
        s.id(),
        s.type(),
        s.legalName(),
        s.tradeName(),
        s.cpf(),
        s.cnpj(),
        s.rg(),
        s.ie(),
        s.im(),
        s.ieExempt(),
        s.birthDate(),
        s.foundationDate(),
        s.notes(),
        s.segmentId(),
        s.regionId(),
        s.active(),
        s.deactivatedAt(),
        s.anonymized(),
        s.activeModalities(),
        s.createdAt(),
        s.updatedAt());
  }
}
