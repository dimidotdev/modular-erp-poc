package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Contrato não encontrado no tenant atual (ou soft-deleted). HTTP 404. */
public class ContractNotFoundException extends DomainException {

  public ContractNotFoundException(ContractId id) {
    super(
        "contract.not-found",
        HttpStatus.NOT_FOUND,
        "Contrato não encontrado",
        "Contrato " + id.value() + " não existe no tenant atual.");
  }
}
