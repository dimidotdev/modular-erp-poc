package dev.dimi.erp.contratos;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Contrato saiu de {@code DRAFT} para {@code ACTIVE}. A partir daqui o job de slice 10.2 começa a
 * gerar {@code billing_cycle}s. Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code auditoria}: registra com categoria {@code CONTRATOS}.
 *   <li>{@code financeiro} (slice 10.2): liga geração automática de MVF a receber.
 * </ul>
 */
public record ContractActivatedEvent(
    ContractId id,
    TenantId tenantId,
    LocalDate startDate,
    UserId activatedBy,
    Instant occurredAt) {}
