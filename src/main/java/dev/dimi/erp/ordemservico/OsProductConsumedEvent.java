package dev.dimi.erp.ordemservico;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Item de produto consumido — estoque OUTBOUND já foi efetuado na mesma transação via {@code
 * StockInventoryPort} (o módulo {@code estoque} é quem persiste o movimento). Este evento é apenas
 * notificação para {@code auditoria}/futuros consumers; estoque NÃO precisa reagir a ele.
 *
 * <p>Evento gordinho — carrega {@code stockMovementId} para o consumer rastrear a movimentação sem
 * lookup.
 */
public record OsProductConsumedEvent(
    OsProductId productId,
    ServiceOrderId osId,
    TenantId tenantId,
    PackagingId packagingId,
    BigDecimal quantity,
    UUID stockMovementId,
    UserId actor,
    Instant occurredAt) {}
