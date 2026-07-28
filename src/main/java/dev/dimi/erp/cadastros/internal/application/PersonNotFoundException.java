package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link PersonId} não encontrou pessoa no tenant atual. */
public class PersonNotFoundException extends DomainException {

  private final PersonId personId;

  public PersonNotFoundException(PersonId personId) {
    super(
        "cadastros.person-not-found",
        HttpStatus.NOT_FOUND,
        "Pessoa não encontrada",
        "Não existe pessoa com este id neste tenant.");
    this.personId = personId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("personId", personId.value().toString());
  }
}
