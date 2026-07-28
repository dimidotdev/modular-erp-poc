package dev.dimi.erp.financeiro.internal.web;

import dev.dimi.erp.financeiro.PaymentId;
import dev.dimi.erp.financeiro.internal.application.InvalidFinancialOperationException;
import dev.dimi.erp.financeiro.internal.application.ProcessPaymentBatchService;
import dev.dimi.erp.financeiro.internal.application.ReverseFinancialPaymentCommand;
import dev.dimi.erp.financeiro.internal.application.ReverseFinancialPaymentService;
import dev.dimi.erp.financeiro.internal.web.dto.FinancialPaymentApiResponse;
import dev.dimi.erp.financeiro.internal.web.dto.ProcessPaymentBatchApiRequest;
import dev.dimi.erp.financeiro.internal.web.dto.ReverseFinancialPaymentApiRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code financial_payment} ({@code /api/v1/financeiro/payments/**}).
 *
 * <p>POST de batch exige {@code Idempotency-Key} (RN-FIN-008); estorno é operação sensível e exige
 * step-up auth (RN-FIN-013).
 */
@RestController
@RequestMapping("/api/v1/financeiro/payments")
@RequiredArgsConstructor
class FinancialPaymentController {

  private final ProcessPaymentBatchService processBatchService;
  private final ReverseFinancialPaymentService reverseService;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('pagamento.registrar')")
  FinancialPaymentApiResponse processBatch(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid ProcessPaymentBatchApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new InvalidFinancialOperationException(
          "idempotency-key-required", "Header Idempotency-Key é obrigatório (RN-FIN-008).");
    }
    return FinancialPaymentApiResponse.from(
        processBatchService.processBatch(body.toCommand(idempotencyKey, principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('pagamento.consultar')")
  FinancialPaymentApiResponse findById(@PathVariable("id") UUID id) {
    return FinancialPaymentApiResponse.from(processBatchService.findById(new PaymentId(id)));
  }

  @PostMapping("/{id}/reverse")
  @PreAuthorize("hasAuthority('pagamento.estornar')")
  FinancialPaymentApiResponse reverse(
      @PathVariable("id") UUID id,
      @RequestBody @Valid ReverseFinancialPaymentApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return FinancialPaymentApiResponse.from(
        reverseService.reverse(
            new ReverseFinancialPaymentCommand(
                new PaymentId(id), body.reason(), principal.userId())));
  }
}
