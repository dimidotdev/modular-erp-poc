package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonType;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de cadastrar pessoa com CPF/CNPJ já presente no tenant.
 *
 * <p>O índice parcial {@code uq_person_cpf}/{@code uq_person_cnpj} bloqueia no Postgres com
 * predicado {@code anonymized=false}. Este erro 409 é a forma idiomática de comunicar isso para o
 * cliente antes de chegar no banco.
 */
public class PersonAlreadyExistsException extends DomainException {

  private final PersonType personType;
  private final String document;

  public PersonAlreadyExistsException(PersonType personType, String document) {
    super(
        "cadastros.person-already-exists",
        HttpStatus.CONFLICT,
        "Pessoa já cadastrada",
        "Já existe pessoa ativa com este " + (personType == PersonType.PF ? "CPF" : "CNPJ") + ".");
    this.personType = personType;
    this.document = document;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("personType", personType.name(), "document", document);
  }
}
