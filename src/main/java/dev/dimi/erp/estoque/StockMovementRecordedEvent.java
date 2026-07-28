package dev.dimi.erp.estoque;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Movimento gravado no ledger (entrada, saída, transferência, ajuste ou estorno). Consumido por
 * {@code auditoria} (categoria {@code ESTOQUE}) e por {@code fiscal} quando necessário recalcular
 * custo para informar em NFe.
 *
 * @param movementId id do movimento gravado
 * @param tenantId tenant da operação
 * @param packagingId SKU afetado
 * @param branchId filial
 * @param direction sentido do movimento
 * @param quantity quantidade (sempre positiva; sentido vem de {@code direction})
 * @param unitCost custo unitário quando aplicável (INBOUND geralmente; null em OUTBOUND/ADJUSTMENT)
 * @param originType módulo de origem (sale, purchase, transfer, adjustment, os) ou null
 * @param originId id da origem cross-module
 * @param recordedBy usuário que disparou
 * @param occurredAt timestamp lógico do movimento (pode ≠ createdAt em ajustes retroativos)
 */
public record StockMovementRecordedEvent(
    MovementId movementId,
    TenantId tenantId,
    PackagingId packagingId,
    BranchId branchId,
    Direction direction,
    BigDecimal quantity,
    BigDecimal unitCost,
    String originType,
    UUID originId,
    UserId recordedBy,
    Instant occurredAt) {}
