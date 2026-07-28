package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de finalizar (clock-out) um apontamento de mão-de-obra que já tem {@code finished_at}
 * preenchido. Apontamento é imutável depois de finalizado — para corrigir, criar novo.
 */
public class LaborAlreadyFinishedException extends DomainException {

  private final OsLaborId laborId;

  public LaborAlreadyFinishedException(OsLaborId laborId) {
    super(
        "os.labor-already-finished",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Apontamento já finalizado",
        "Apontamento de mão-de-obra " + laborId.value() + " já foi finalizado.");
    this.laborId = laborId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("laborId", laborId.value());
  }
}
