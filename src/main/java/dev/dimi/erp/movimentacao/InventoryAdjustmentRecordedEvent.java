package dev.dimi.erp.movimentacao;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Ajuste de inventário por conferência registrado (RN-MOV-024). Cada linha gerou um movimento
 * ADJUSTMENT no ledger de estoque via {@code StockInventoryPort.adjust}. {@code linesCount} permite
 * que o consumidor (auditoria) saiba o tamanho da operação sem buscar.
 */
public record InventoryAdjustmentRecordedEvent(
    InventoryAdjustmentId adjustmentId,
    TenantId tenantId,
    BranchId branchId,
    int linesCount,
    UserId recordedBy,
    Instant occurredAt) {}
