package dev.dimi.erp.movimentacao;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Transferência fechada — par OUTBOUND/INBOUND atômico já aplicado via {@code
 * StockInventoryPort.transfer}. RN-MOV-019: transferência entre filiais com mesmo CNPJ não gera NFe
 * (v1 default). RN-MOV-020: entre CNPJs distintos vai gerar NFe (slice fiscal).
 */
public record StockTransferClosedEvent(
    StockTransferId transferId,
    TenantId tenantId,
    BranchId originBranchId,
    BranchId destBranchId,
    UserId closedBy,
    Instant occurredAt) {}
