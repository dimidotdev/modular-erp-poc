package dev.dimi.erp.estoque;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ajuste manual de estoque realizado (RN-EST-010). Operação sensível: dispara audit log reforçado.
 * {@code delta} é positivo se entrada, negativo se saída (em ADJUSTMENT carregamos o sinal aqui
 * para conveniência do consumidor, ao contrário do {@code stock_movement.quantity} que é sempre
 * positiva).
 */
public record StockAdjustmentRecordedEvent(
    MovementId movementId,
    TenantId tenantId,
    BigDecimal delta,
    String reason,
    UserId actor,
    Instant occurredAt) {}
