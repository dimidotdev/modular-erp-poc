package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.address.Cep;
import java.util.UUID;

/**
 * Comando para criar um endereço da pessoa. {@code cep} já deve vir validado via {@link Cep}.
 *
 * <p>Quando {@code defaultAddress=true}, o service desmarca o atual padrão (RN-CAD-004) antes de
 * salvar o novo.
 */
public record CreatePersonAddressCommand(
    PersonId personId,
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
    boolean defaultAddress,
    UserId createdBy) {

  public CreatePersonAddressCommand {
    if (personId == null) throw new IllegalArgumentException("personId obrigatório");
    if (cep == null || cep.isBlank()) throw new IllegalArgumentException("cep obrigatório");
    if (street == null || street.isBlank())
      throw new IllegalArgumentException("street obrigatório");
    if (city == null || city.isBlank()) throw new IllegalArgumentException("city obrigatório");
    if (state == null || state.length() != 2)
      throw new IllegalArgumentException("state deve ter 2 caracteres");
  }
}
