package dev.dimi.erp.estoque;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Reserva liberada sem efetivar saída (cancelamento de pedido, expiração de carrinho). Saldo
 * disponível volta a subir.
 */
public record StockReservationReleasedEvent(
    TenantId tenantId,
    PackagingId packagingId,
    BranchId branchId,
    BigDecimal quantity,
    String originType,
    UUID originId,
    Instant occurredAt) {}
