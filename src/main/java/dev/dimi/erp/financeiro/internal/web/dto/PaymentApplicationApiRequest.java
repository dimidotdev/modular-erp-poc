package dev.dimi.erp.financeiro.internal.web.dto;

import dev.dimi.erp.financeiro.InstallmentId;
import dev.dimi.erp.financeiro.internal.application.ProcessPaymentBatchCommand;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de aplicação de pagamento em uma parcela. */
public record PaymentApplicationApiRequest(
    @NotNull UUID installmentId,
    @NotNull @Positive BigDecimal value,
    @PositiveOrZero BigDecimal fineApplied,
    @PositiveOrZero BigDecimal interestApplied,
    @PositiveOrZero BigDecimal discountApplied) {

  public ProcessPaymentBatchCommand.Application toCommand() {
    return new ProcessPaymentBatchCommand.Application(
        new InstallmentId(installmentId), value, fineApplied, interestApplied, discountApplied);
  }
}
