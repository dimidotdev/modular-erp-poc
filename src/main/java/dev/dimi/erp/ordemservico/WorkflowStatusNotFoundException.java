package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Workflow status referenciado por ID ou code não existe no tenant. */
public class WorkflowStatusNotFoundException extends DomainException {

  public WorkflowStatusNotFoundException(String reference) {
    super(
        "os.workflow-status-not-found",
        HttpStatus.NOT_FOUND,
        "Status de workflow não encontrado",
        "Status '" + reference + "' não existe no workflow deste tenant.");
  }
}
