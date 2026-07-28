package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de editar OS em status read-only ({@code CLOSED} = fechada definitiva, {@code BILLED} =
 * apenas observações em texto livre são permitidas, mas itens/totais não). Regra fica enxuta no
 * 9.1; 9.2 endurece quando itens entrarem.
 */
public class ServiceOrderNotEditableException extends DomainException {

  public ServiceOrderNotEditableException(ServiceOrderId id, String statusCode) {
    super(
        "os.not-editable",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Ordem de Serviço não pode ser editada",
        "OS " + id.value() + " está em status '" + statusCode + "' e não permite edição.");
  }
}
