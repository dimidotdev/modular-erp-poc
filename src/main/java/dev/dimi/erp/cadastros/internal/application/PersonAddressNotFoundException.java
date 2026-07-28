package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonAddressId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link PersonAddressId} não encontrou endereço para a pessoa no tenant atual. */
public class PersonAddressNotFoundException extends DomainException {

  private final PersonId personId;
  private final PersonAddressId addressId;

  public PersonAddressNotFoundException(PersonId personId, PersonAddressId addressId) {
    super(
        "cadastros.person-address-not-found",
        HttpStatus.NOT_FOUND,
        "Endereço não encontrado",
        "Não existe endereço com este id para a pessoa no tenant atual.");
    this.personId = personId;
    this.addressId = addressId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of(
        "personId", personId.value().toString(),
        "addressId", addressId.value().toString());
  }
}
