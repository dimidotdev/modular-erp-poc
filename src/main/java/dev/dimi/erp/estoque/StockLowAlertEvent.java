package dev.dimi.erp.estoque;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Alerta de saldo baixo (RN-EST-015). v1 usa threshold fixo configurável; v1.x admitirá threshold
 * por SKU/filial. Emitido pelo {@code StockInventoryService} sempre que um OUTBOUND ou
 * SHIP_RESERVATION deixa {@code quantity_on_hand} abaixo do limite.
 */
public record StockLowAlertEvent(
    TenantId tenantId,
    PackagingId packagingId,
    BranchId branchId,
    BigDecimal quantityOnHand,
    BigDecimal threshold,
    Instant occurredAt) {}
