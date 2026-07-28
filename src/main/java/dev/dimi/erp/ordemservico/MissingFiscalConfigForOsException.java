package dev.dimi.erp.ordemservico;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/**
 * Disparada durante o faturamento da OS quando o módulo fiscal não consegue carregar a {@code
 * branch_fiscal} da filial. Sinaliza falta de configuração fiscal (certificado A1, série, regime
 * tributário) — pré-requisito para emissão simultânea de NFe + NFSe.
 */
public class MissingFiscalConfigForOsException extends DomainException {

  public MissingFiscalConfigForOsException(BranchId branchId) {
    super(
        "os.missing-fiscal-config",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Filial sem configuração fiscal",
        "Filial "
            + branchId.value()
            + " não possui branch_fiscal configurado — faturamento da OS recusado.");
  }
}
