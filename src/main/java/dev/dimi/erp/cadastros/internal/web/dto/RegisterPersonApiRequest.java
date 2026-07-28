package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonType;
import dev.dimi.erp.cadastros.internal.application.RegisterPersonCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import java.util.UUID;

/**
 * Request body do {@code POST /api/v1/cadastros/persons}.
 *
 * <p>Validação cruzada de {@code type} x {@code cpf}/{@code cnpj} é feita no service (Jakarta
 * Validation não expressa bem essa regra polimórfica sem groups). DV do CPF/CNPJ é validado pelo
 * {@link dev.dimi.erp.shared.documents.Cpf}/{@link dev.dimi.erp.shared.documents.Cnpj} dentro do
 * service.
 */
public record RegisterPersonApiRequest(
    @NotNull PersonType type,
    @NotBlank @Size(max = 200) String legalName,
    @Size(max = 200) String tradeName,
    @Size(max = 18) String cpf,
    @Size(max = 18) String cnpj,
    @Size(max = 30) String rg,
    @Size(max = 30) String ie,
    @Size(max = 30) String im,
    Boolean ieExempt,
    LocalDate birthDate,
    LocalDate foundationDate,
    @Size(max = 2000) String notes,
    UUID segmentId,
    UUID regionId,
    @NotEmpty Set<Modality> modalities) {

  public RegisterPersonCommand toCommand(UserId registeredBy) {
    return new RegisterPersonCommand(
        type,
        legalName,
        tradeName,
        cpf,
        cnpj,
        rg,
        ie,
        im,
        ieExempt != null && ieExempt,
        birthDate,
        foundationDate,
        notes,
        segmentId,
        regionId,
        modalities,
        registeredBy);
  }
}
