package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Comando para atualização parcial de pessoa. Campos {@code null} são preservados (PATCH
 * semantics). {@code type}, {@code cpf} e {@code cnpj} são imutáveis (RN-CAD-003) e ficam de fora.
 */
public record UpdatePersonCommand(
    PersonId personId,
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
    UUID regionId,
    UserId updatedBy) {}
