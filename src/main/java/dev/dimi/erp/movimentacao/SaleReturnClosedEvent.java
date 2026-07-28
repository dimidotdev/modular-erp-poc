package dev.dimi.erp.movimentacao;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Devolução fechada. Marco do fluxo: estoque das quantidades returned já voltou via {@code
 * StockInventoryPort.receive} (sem custo — preserva WAC). Consumido por:
 *
 * <ul>
 *   <li>{@code financeiro} (slice 7) — estorna MVF proporcionalmente (RN-MOV-018).
 *   <li>{@code fiscal} (slice 8) — emite NFe de devolução (RN-MOV-017).
 *   <li>{@code auditoria}.
 * </ul>
 */
public record SaleReturnClosedEvent(
    SaleReturnId returnId,
    SaleId saleId,
    TenantId tenantId,
    BranchId branchId,
    BigDecimal refundValue,
    UserId closedBy,
    Instant occurredAt) {}
