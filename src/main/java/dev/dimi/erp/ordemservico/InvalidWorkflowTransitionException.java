package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Transição entre status não existe no grafo {@code os_workflow_transition}. Cliente deve consultar
 * {@code GET /workflow/transitions} (futuro) para descobrir transições válidas a partir do status
 * atual.
 */
public class InvalidWorkflowTransitionException extends DomainException {

  private final String fromCode;
  private final String toCode;

  public InvalidWorkflowTransitionException(String fromCode, String toCode) {
    super(
        "os.invalid-transition",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Transição de status não permitida",
        "Transição de '" + fromCode + "' para '" + toCode + "' não está cadastrada no workflow.");
    this.fromCode = fromCode;
    this.toCode = toCode;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("from", fromCode, "to", toCode);
  }
}
