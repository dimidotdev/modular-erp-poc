package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Transição de status inválida no ciclo de vida do contrato (ex.: {@code DRAFT → SUSPENDED} ou
 * {@code CANCELLED → ACTIVE}). Ver {@link ContractStatus} para o grafo válido.
 */
public class InvalidContractTransitionException extends DomainException {

  private final ContractStatus from;
  private final ContractStatus to;

  public InvalidContractTransitionException(ContractStatus from, ContractStatus to) {
    super(
        "contract.invalid-transition",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Transição de status não permitida",
        "Transição de '" + from + "' para '" + to + "' não é válida no ciclo de vida do contrato.");
    this.from = from;
    this.to = to;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("from", from.name(), "to", to.name());
  }
}
