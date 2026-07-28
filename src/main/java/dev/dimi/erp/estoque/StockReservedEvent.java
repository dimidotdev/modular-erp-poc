package dev.dimi.erp.estoque;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva de saldo virtual realizada (pedido em aberto, contrato com demanda futura). RN-EST-007:
 * reserva incrementa {@code quantity_reserved} sem mexer em {@code quantity_on_hand} — saldo
 * disponível ({@code on_hand - reserved}) cai.
 */
public record StockReservedEvent(
    TenantId tenantId,
    PackagingId packagingId,
    BranchId branchId,
    BigDecimal quantity,
    String originType,
    UUID originId,
    Instant occurredAt) {}
