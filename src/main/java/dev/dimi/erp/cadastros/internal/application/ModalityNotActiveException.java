package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Tentativa de desativar uma modalidade que não está ativa (ou nunca foi atribuída). */
public class ModalityNotActiveException extends DomainException {

  private final PersonId personId;
  private final Modality modality;

  public ModalityNotActiveException(PersonId personId, Modality modality) {
    super(
        "cadastros.modality-not-active",
        HttpStatus.CONFLICT,
        "Modalidade não ativa",
        "A modalidade não está ativa para esta pessoa.");
    this.personId = personId;
    this.modality = modality;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of(
        "personId", personId.value().toString(),
        "modality", modality.name());
  }
}
