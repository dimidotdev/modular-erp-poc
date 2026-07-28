package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.BankAccountId;
import dev.dimi.erp.financeiro.PaymentMethod;
import dev.dimi.erp.financeiro.internal.application.ProcessPaymentBatchCommand;
import dev.dimi.erp.identidade.UserId;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Payload de POST /api/v1/financeiro/payments. Idempotency-Key header obrigatório (controller
 * valida).
 */
public record ProcessPaymentBatchApiRequest(
    @NotNull UUID bankAccountId,
    @NotNull PaymentMethod method,
    Instant occurredAt,
    @Size(max = 2000) String notes,
    @NotEmpty @Valid List<PaymentApplicationApiRequest> applications) {

  public ProcessPaymentBatchCommand toCommand(String idempotencyKey, UserId actor) {
    return new ProcessPaymentBatchCommand(
        new BankAccountId(bankAccountId),
        method,
        occurredAt,
        applications.stream().map(PaymentApplicationApiRequest::toCommand).toList(),
        notes,
        idempotencyKey,
        actor);
  }
}
