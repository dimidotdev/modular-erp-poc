package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Geração de ciclo de faturamento falhou — alerta P3 para operação. O ciclo fica em {@code FAILED}
 * e pode ser regenerado manualmente após correção da causa raiz. Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code auditoria}: registra com categoria {@code CONTRATOS}.
 *   <li>Observabilidade: dispara alerta P3 (canal de operações).
 * </ul>
 */
public record ContractCycleFailedEvent(
    BillingCycleId cycleId,
    ContractId contractId,
    TenantId tenantId,
    LocalDate competenceMonth,
    String reason,
    Instant occurredAt) {}
