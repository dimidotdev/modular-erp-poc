package dev.dimi.erp.contratos;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Aditivo contratual efetivado — o estado do contrato já foi materializado (baseValue atualizado
 * para VALUE; endDate prorrogado para EXTENSION; item adicionado/removido para ITEM_*). Os campos
 * {@code newBaseValue} e {@code newEndDate} podem ser {@code null} quando o tipo de aditivo não
 * altera o respectivo campo.
 *
 * <ul>
 *   <li>{@code auditoria}: registra com categoria {@code CONTRATOS}.
 *   <li>{@code financeiro} (slice 10.2): recalcula ciclos futuros com base nos novos valores.
 * </ul>
 */
public record ContractAmendedEvent(
    AmendmentId amendmentId,
    ContractId contractId,
    TenantId tenantId,
    AmendmentType type,
    LocalDate effectiveFrom,
    BigDecimal newBaseValue,
    LocalDate newEndDate,
    UserId effectivatedBy,
    Instant occurredAt) {}
