package dev.dimi.erp.fiscal;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * RN-FIS-001: a filial referenciada não tem {@code branch_fiscal} configurado ou {@code
 * BranchQueryPort.canEmitInvoice} retornou {@code false}. Emissão recusada com 422.
 */
public class BranchNotFiscallyConfiguredException extends DomainException {

  public BranchNotFiscallyConfiguredException(String branchId) {
    super(
        "fiscal.branch-not-fiscally-configured",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Filial sem configuração fiscal",
        "Filial " + branchId + " não está configurada para emissão fiscal (RN-FIS-001).");
  }
}
