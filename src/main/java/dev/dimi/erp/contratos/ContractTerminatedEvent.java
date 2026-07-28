package dev.dimi.erp.contratos;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Contrato encerrado — cobre os 3 motivos terminais: {@code EXPIRED} (atingiu endDate sem
 * renovação), {@code CANCELLED} (rescisão administrativa) e {@code TERMINATED_EARLY} (rescisão
 * antecipada com {@code cancellationFee}). O flag {@code earlyTermination} dispara cobrança da
 * multa pelo financeiro. Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code auditoria}: registra com categoria {@code CONTRATOS} (sensível).
 *   <li>{@code financeiro} (slice 10.2): se {@code earlyTermination=true} e {@code cancellationFee
 *       > 0}, gera MVF de multa.
 * </ul>
 */
public record ContractTerminatedEvent(
    ContractId id,
    TenantId tenantId,
    LocalDate terminationDate,
    String reason,
    BigDecimal cancellationFee,
    boolean earlyTermination,
    UserId terminatedBy,
    Instant occurredAt) {}
