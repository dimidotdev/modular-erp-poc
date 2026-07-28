package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.UpdatePersonCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Request body do {@code PATCH /api/v1/cadastros/persons/{id}}. Semântica de PATCH: campos {@code
 * null} são preservados; só os enviados são alterados.
 */
public record UpdatePersonApiRequest(
    @Size(max = 200) String legalName,
    @Size(max = 200) String tradeName,
    @Size(max = 30) String rg,
    @Size(max = 30) String ie,
    @Size(max = 30) String im,
    Boolean ieExempt,
    LocalDate birthDate,
    LocalDate foundationDate,
    @Size(max = 2000) String notes,
    UUID segmentId,
    UUID regionId) {

  public UpdatePersonCommand toCommand(PersonId personId, UserId updatedBy) {
    return new UpdatePersonCommand(
        personId,
        legalName,
        tradeName,
        rg,
        ie,
        im,
        ieExempt,
        birthDate,
        foundationDate,
        notes,
        segmentId,
        regionId,
        updatedBy);
  }
}
