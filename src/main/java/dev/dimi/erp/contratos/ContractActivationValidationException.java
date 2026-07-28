package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Falhou validação ao ativar contrato — ex.: sem itens, {@code baseValue=0}, {@code endDate}
 * anterior a {@code startDate}, {@code billingDay} fora de {@code [1..31]}. Mensagem específica vem
 * em {@code reason} pra exibição direta ao operador.
 */
public class ContractActivationValidationException extends DomainException {

  public ContractActivationValidationException(String reason) {
    super(
        "contract.activation-invalid",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Falhou validação ao ativar contrato",
        reason);
  }
}
