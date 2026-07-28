package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de modificar/deletar status marcado como {@code is_system=true} (OPEN, IN_PROGRESS,
 * AWAITING_PARTS, READY, BILLED, CLOSED, CANCELLED). Status custom criados pelo tenant podem ser
 * livremente alterados/removidos.
 */
public class SystemWorkflowStatusImmutableException extends DomainException {

  public SystemWorkflowStatusImmutableException(String code) {
    super(
        "os.system-status-immutable",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Status do sistema não pode ser modificado",
        "O status '" + code + "' é do sistema e não pode ser alterado nem removido.");
  }
}
