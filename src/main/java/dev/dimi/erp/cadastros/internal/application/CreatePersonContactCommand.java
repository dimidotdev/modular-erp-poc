package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import java.util.UUID;

/**
 * Comando para criar um contato (telefone, email, etc) para uma pessoa. {@code value} é livre — o
 * tipo (via {@code contactTypeId}) qualifica a interpretação.
 *
 * <p>Quando {@code primaryContact=true}, o service desmarca o primário anterior antes de salvar.
 */
public record CreatePersonContactCommand(
    PersonId personId,
    UUID contactTypeId,
    String value,
    String label,
    boolean primaryContact,
    UserId createdBy) {

  public CreatePersonContactCommand {
    if (personId == null) throw new IllegalArgumentException("personId obrigatório");
    if (value == null || value.isBlank()) throw new IllegalArgumentException("value obrigatório");
  }
}
