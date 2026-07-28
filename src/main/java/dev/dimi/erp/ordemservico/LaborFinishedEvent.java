package dev.dimi.erp.ordemservico;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Apontamento de mão-de-obra finalizado (clock-out). Consumidores conhecidos: {@code auditoria}; no
 * futuro {@code financeiro} pode usar {@code hours} para custeio.
 *
 * <p>Evento gordinho — leva {@code hours} (já calculado a partir do intervalo {@code startedAt}-
 * {@code finishedAt}) pra consumer dispensar QueryPort.
 */
public record LaborFinishedEvent(
    OsLaborId laborId,
    ServiceOrderId osId,
    TenantId tenantId,
    BigDecimal hours,
    Instant finishedAt,
    UserId actor,
    Instant occurredAt) {}
