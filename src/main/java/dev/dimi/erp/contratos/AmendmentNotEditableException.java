package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de editar ou cancelar aditivo já {@code EFFECTIVE} (efeito materializado no contrato)
 * ou {@code CANCELLED} (estado terminal). Aditivos só são editáveis em {@code DRAFT}.
 */
public class AmendmentNotEditableException extends DomainException {

  private final AmendmentId id;
  private final String status;

  public AmendmentNotEditableException(AmendmentId id, String status) {
    super(
        "amendment.not-editable",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Aditivo não editável",
        "Aditivo "
            + id.value()
            + " está em status "
            + status
            + " — edição só é permitida em DRAFT.");
    this.id = id;
    this.status = status;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("amendmentId", id.value().toString(), "status", status);
  }
}
