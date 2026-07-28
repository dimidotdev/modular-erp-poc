package dev.dimi.erp.contratos;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Ciclo de faturamento do contrato foi materializado — {@code financeiro} deve criar o MVF a
 * receber correspondente. Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code financeiro} (slice 10.2): cria MVF + parcelas via {@code paymentConditionId} (ou
 *       fallback 30 dias quando {@code null}).
 *   <li>{@code auditoria}: registra com categoria {@code CONTRATOS}.
 * </ul>
 *
 * <p>Evento "gordinho" propositalmente — segue {@code feedback_event_enrichment_over_queryport}:
 * carrega tudo que financeiro precisa pra criar o MVF sem voltar via QueryPort. Campos opcionais
 * ({@code contactId}, {@code paymentConditionId}, {@code costCenterId}, {@code triggeredBy}) podem
 * vir {@code null}.
 */
public record ContractInvoiceGeneratedEvent(
    BillingCycleId cycleId,
    ContractId contractId,
    TenantId tenantId,
    BranchId branchId,
    PersonId customerId,
    UUID contactId,
    UUID paymentConditionId,
    UUID costCenterId,
    LocalDate competenceMonth,
    LocalDate dueDate,
    BigDecimal value,
    String contractTitle,
    UserId triggeredBy,
    Instant occurredAt) {}
