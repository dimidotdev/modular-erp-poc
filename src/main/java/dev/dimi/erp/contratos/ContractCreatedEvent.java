package dev.dimi.erp.contratos;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Contrato criado em {@code DRAFT}. Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code auditoria}: registra com categoria {@code CONTRATOS}.
 * </ul>
 *
 * <p>Evento "gordinho" propositalmente — segue {@code feedback_event_enrichment_over_queryport}:
 * carrega todos os campos que consumers comuns precisam, evitando voltar via {@code
 * ContractQueryPort} (que só existe para listas/lookups pós-criação).
 */
public record ContractCreatedEvent(
    ContractId id,
    TenantId tenantId,
    BranchId branchId,
    PersonId customerId,
    long number,
    BigDecimal baseValue,
    Recurrence recurrence,
    int billingDay,
    LocalDate startDate,
    LocalDate endDate,
    UserId createdBy,
    Instant occurredAt) {}
