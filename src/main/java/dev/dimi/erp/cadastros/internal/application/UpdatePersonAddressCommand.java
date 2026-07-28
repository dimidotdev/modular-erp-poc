package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonAddressId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import java.util.UUID;

/**
 * Atualização parcial de endereço. Campos {@code null} preservam o valor atual. Não cobre alteração
 * da flag {@code is_default} — para isso usar endpoint dedicado {@code .../padrao}.
 */
public record UpdatePersonAddressCommand(
    PersonId personId,
    PersonAddressId addressId,
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
    UserId updatedBy) {

  public UpdatePersonAddressCommand {
    if (personId == null) throw new IllegalArgumentException("personId obrigatório");
    if (addressId == null) throw new IllegalArgumentException("addressId obrigatório");
  }
}
