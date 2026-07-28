package dev.dimi.erp.ordemservico;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item de produto adicionado à OS. Consumidores conhecidos: {@code auditoria}; no futuro {@code
 * financeiro} pode reagir para previsão de receita.
 *
 * <p>Evento gordinho — carrega {@code packagingId}, {@code quantity} e {@code netValue} pra
 * consumer não precisar reabrir via QueryPort. {@code consumedAt} ainda é {@code null} aqui — esse
 * é o evento de adição (lançamento na OS), não de consumo de estoque (esse vem em {@link
 * OsProductConsumedEvent}).
 */
public record OsProductAddedEvent(
    OsProductId productId,
    ServiceOrderId osId,
    TenantId tenantId,
    PackagingId packagingId,
    BigDecimal quantity,
    BigDecimal netValue,
    UserId actor,
    Instant occurredAt) {}
