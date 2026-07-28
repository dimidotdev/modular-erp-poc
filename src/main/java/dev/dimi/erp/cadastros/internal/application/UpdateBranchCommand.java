package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import java.util.UUID;

/**
 * Alteração parcial de filial. {@code null} preserva o valor atual; setar {@code isMain=true}
 * promove esta filial a matriz e desmarca a anterior (RN-CAD-014). {@code code} é imutável.
 */
public record UpdateBranchCommand(
    BranchId branchId, String name, UUID addressId, Boolean isMain, UserId updatedBy) {}
