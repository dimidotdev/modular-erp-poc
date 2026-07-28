package dev.dimi.erp.produto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Tabela de preço criada ou atualizada. Emite-se a cada upsert de tabela ou de itens (batch) — para
 * o consumidor decidir se invalida cache local.
 *
 * @param priceTableId id da tabela
 * @param tenantId tenant da operação
 * @param branchId filial-alvo (null = tabela global do tenant)
 * @param itemsTouched quantidade de itens criados/atualizados/removidos na operação atual
 * @param updatedBy usuário que disparou
 * @param occurredAt timestamp da operação
 */
public record PriceTableUpdatedEvent(
    PriceTableId priceTableId,
    TenantId tenantId,
    BranchId branchId,
    int itemsTouched,
    UserId updatedBy,
    Instant occurredAt) {}
