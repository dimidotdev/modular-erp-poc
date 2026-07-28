package dev.dimi.erp.contratos;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Reajuste aplicado ao contrato — distinto de {@link ContractAmendedEvent} com tipo {@code VALUE}:
 * aqui o motivo é indexação ({@code IGPM/IPCA/INPC}) ou ajuste manual. O contrato já teve {@code
 * baseValue} substituído por {@code newBaseValue}. Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code auditoria}: registra com categoria {@code CONTRATOS}.
 *   <li>{@code financeiro} (slice 10.2): ciclos futuros usam {@code newBaseValue}.
 * </ul>
 */
public record ContractAdjustedEvent(
    AdjustmentId adjustmentId,
    ContractId contractId,
    TenantId tenantId,
    AdjustmentIndex index,
    BigDecimal percentage,
    BigDecimal oldBaseValue,
    BigDecimal newBaseValue,
    LocalDate appliedOn,
    UserId appliedBy,
    Instant occurredAt) {}
