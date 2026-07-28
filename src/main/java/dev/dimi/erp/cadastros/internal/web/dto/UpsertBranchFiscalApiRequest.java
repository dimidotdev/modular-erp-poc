package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.TaxRegime;
import dev.dimi.erp.cadastros.internal.application.UpsertBranchFiscalCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.Size;

/**
 * Request body do {@code PUT /api/v1/cadastros/branches/{id}/fiscal}.
 *
 * <p>CNPJ obrigatório no primeiro registro e imutável nos subsequentes — validação cruzada feita no
 * service. Regime tributário também obrigatório no primeiro registro.
 */
public record UpsertBranchFiscalApiRequest(
    @Size(max = 18) String cnpj,
    @Size(max = 30) String ie,
    @Size(max = 30) String im,
    Boolean ieExempt,
    TaxRegime taxRegime,
    @Size(max = 3) String csosnDefault,
    @Size(max = 2) String cstIcmsDefault,
    @Size(max = 10) String cnae,
    @Size(max = 20) String nfeEnvironment) {

  public UpsertBranchFiscalCommand toCommand(BranchId branchId, UserId actor) {
    return new UpsertBranchFiscalCommand(
        branchId,
        cnpj,
        ie,
        im,
        ieExempt,
        taxRegime,
        csosnDefault,
        cstIcmsDefault,
        cnae,
        nfeEnvironment,
        actor);
  }
}
