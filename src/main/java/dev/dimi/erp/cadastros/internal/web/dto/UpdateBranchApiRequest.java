package dev.dimi.erp.cadastros.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.internal.application.UpdateBranchCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * Request body do {@code PATCH /api/v1/cadastros/branches/{id}}. {@code code} é imutável.
 *
 * <p>Setar {@code isMain=true} promove esta filial a matriz e desmarca a anterior (RN-CAD-014);
 * para desmarcar uma matriz, promova outra filial — não há "rebaixamento" direto.
 */
public record UpdateBranchApiRequest(@Size(max = 120) String name, UUID addressId, Boolean isMain) {

  public UpdateBranchCommand toCommand(BranchId branchId, UserId updatedBy) {
    return new UpdateBranchCommand(branchId, name, addressId, isMain, updatedBy);
  }
}
