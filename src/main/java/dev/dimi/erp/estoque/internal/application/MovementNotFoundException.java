package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.estoque.MovementId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link MovementId} não encontrou movimento no tenant atual. */
public class MovementNotFoundException extends DomainException {

  private final MovementId movementId;

  public MovementNotFoundException(MovementId movementId) {
    super(
        "estoque.movement-not-found",
        HttpStatus.NOT_FOUND,
        "Movimento não encontrado",
        "Não existe movimento com este id neste tenant.");
    this.movementId = movementId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("movementId", movementId.value().toString());
  }
}
