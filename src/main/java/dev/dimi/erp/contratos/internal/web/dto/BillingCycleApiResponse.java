package dev.dimi.erp.contratos.internal.web.dto;

import dev.dimi.erp.contratos.BillingCycleStatus;
import dev.dimi.erp.contratos.internal.domain.BillingCycle;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Representação REST de {@code contract_billing_cycle}. Exposta na listagem {@code GET
 * /api/v1/contratos/{id}/billing-cycles} — payload "plano" (sem aninhar contrato) porque o caller
 * já navegou pelo contrato para chegar aqui.
 *
 * <p>{@code financialRecordId} e {@code invoiceId} ficam nulos enquanto o ciclo é {@code PENDING};
 * {@code failureReason} só é preenchido após transição para {@code FAILED}; {@code notes} carrega o
 * motivo de {@code SKIPPED} (contrato suspenso na competência, etc).
 */
public record BillingCycleApiResponse(
    UUID id,
    UUID contractId,
    LocalDate competenceMonth,
    LocalDate dueDate,
    BigDecimal value,
    BillingCycleStatus status,
    Instant generatedAt,
    String failureReason,
    UUID financialRecordId,
    UUID invoiceId,
    String notes) {

  public static BillingCycleApiResponse from(BillingCycle c) {
    return new BillingCycleApiResponse(
        c.getId(),
        c.getContractId(),
        c.getCompetenceMonth(),
        c.getDueDate(),
        c.getValue(),
        c.getStatus(),
        c.getGeneratedAt(),
        c.getFailureReason(),
        c.getFinancialRecordId(),
        c.getInvoiceId(),
        c.getNotes());
  }
}
