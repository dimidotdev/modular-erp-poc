package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.Priority;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Comando de criação de SC em DRAFT. {@code departmentId} e {@code costCenterId} opcionais — slice
 * 6.2 não força (financeiro ainda não existe).
 */
public record RegisterPurchaseRequestCommand(
    BranchId branchId,
    UUID departmentId,
    UUID costCenterId,
    LocalDate neededBy,
    Priority priority,
    String justification,
    List<Item> items,
    UserId actor) {

  public record Item(
      PackagingId packagingId,
      String description,
      BigDecimal quantity,
      BigDecimal estimatedUnitCost,
      String notes) {}
}
