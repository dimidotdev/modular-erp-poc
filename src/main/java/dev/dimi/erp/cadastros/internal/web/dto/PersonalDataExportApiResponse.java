package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.internal.application.PersonalDataExport;
import dev.dimi.erp.identidade.UserId;
import java.time.Instant;
import java.util.List;

/**
 * Payload do export consolidado de dados pessoais (RN-CAD-016 / LGPD Art. 18 V).
 *
 * <p>Os blocos {@code addresses}, {@code contacts} e {@code employments} são listas vazias quando
 * não houver registros (não {@code null}), simplificando o consumo pelo cliente.
 */
public record PersonalDataExportApiResponse(
    PersonApiResponse person,
    List<PersonAddressApiResponse> addresses,
    List<PersonContactApiResponse> contacts,
    List<EmployeeApiResponse> employments,
    UserId exportedBy,
    Instant exportedAt) {

  public static PersonalDataExportApiResponse from(PersonalDataExport export) {
    return new PersonalDataExportApiResponse(
        PersonApiResponse.from(export.person()),
        export.addresses().stream().map(PersonAddressApiResponse::from).toList(),
        export.contacts().stream().map(PersonContactApiResponse::from).toList(),
        export.employments().stream().map(EmployeeApiResponse::from).toList(),
        export.exportedBy(),
        export.exportedAt());
  }
}
