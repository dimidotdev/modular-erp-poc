package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonContactId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link PersonContactId} não encontrou contato para a pessoa no tenant atual. */
public class PersonContactNotFoundException extends DomainException {

  private final PersonId personId;
  private final PersonContactId contactId;

  public PersonContactNotFoundException(PersonId personId, PersonContactId contactId) {
    super(
        "cadastros.person-contact-not-found",
        HttpStatus.NOT_FOUND,
        "Contato não encontrado",
        "Não existe contato com este id para a pessoa no tenant atual.");
    this.personId = personId;
    this.contactId = contactId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of(
        "personId", personId.value().toString(),
        "contactId", contactId.value().toString());
  }
}
