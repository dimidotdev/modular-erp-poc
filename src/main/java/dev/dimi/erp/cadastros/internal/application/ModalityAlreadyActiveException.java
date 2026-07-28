package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Tentativa de ativar uma modalidade que já está ativa para a pessoa. */
public class ModalityAlreadyActiveException extends DomainException {

  private final PersonId personId;
  private final Modality modality;

  public ModalityAlreadyActiveException(PersonId personId, Modality modality) {
    super(
        "cadastros.modality-already-active",
        HttpStatus.CONFLICT,
        "Modalidade já ativa",
        "A modalidade já está ativa para esta pessoa.");
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
