package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de gerar ciclo de faturamento manual para contrato que não está em {@code ACTIVE}. O
 * job automático só processa contratos {@code ACTIVE}; geração manual segue a mesma regra.
 */
public class ContractNotActiveForCycleException extends DomainException {

  private final ContractId contractId;
  private final ContractStatus status;

  public ContractNotActiveForCycleException(ContractId contractId, ContractStatus status) {
    super(
        "billing-cycle.contract-not-active",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Contrato não ativo para geração de ciclo",
        "Contrato "
            + contractId.value()
            + " está em status "
            + status
            + " — geração de ciclo só é permitida em ACTIVE.");
    this.contractId = contractId;
    this.status = status;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("contractId", contractId.value().toString(), "status", status.name());
  }
}
