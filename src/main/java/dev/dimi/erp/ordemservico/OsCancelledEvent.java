package dev.dimi.erp.ordemservico;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * OS cancelada. Em 9.1, cancelamento só muda status (sem efeitos colaterais — não há itens nem
 * fatura ainda). 9.2 amplia: dispara saga de estorno (estoque + NFe + MVF) quando aplicável.
 * Consumidores: {@code auditoria}; em 9.2 também {@code estoque}, {@code fiscal}, {@code
 * financeiro}.
 */
public record OsCancelledEvent(
    ServiceOrderId osId,
    TenantId tenantId,
    String reason,
    UserId cancelledBy,
    Instant occurredAt) {}
