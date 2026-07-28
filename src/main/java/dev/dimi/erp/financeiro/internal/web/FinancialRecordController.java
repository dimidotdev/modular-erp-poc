package dev.dimi.erp.financeiro.internal.web;

import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.financeiro.internal.application.CancelFinancialRecordCommand;
import dev.dimi.erp.financeiro.internal.application.ManageFinancialRecordService;
import dev.dimi.erp.financeiro.internal.web.dto.CancelFinancialRecordApiRequest;
import dev.dimi.erp.financeiro.internal.web.dto.FinancialRecordApiResponse;
import dev.dimi.erp.financeiro.internal.web.dto.OpenFinancialRecordApiRequest;
import dev.dimi.erp.financeiro.internal.web.dto.RenegotiateFinancialRecordApiRequest;
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
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code financial_record} ({@code /api/v1/financeiro/records/**}). Permissions
 * granulares conforme {@code docs/domain/17-financeiro.md}; cancelar e renegociar exigem step-up
 * (operação contábil sensível).
 */
@RestController
@RequestMapping("/api/v1/financeiro/records")
@RequiredArgsConstructor
class FinancialRecordController {

  private final ManageFinancialRecordService records;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('mvf.criar')")
  FinancialRecordApiResponse open(
      @RequestBody @Valid OpenFinancialRecordApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return FinancialRecordApiResponse.from(records.open(body.toCommand(principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('mvf.consultar')")
  FinancialRecordApiResponse findById(@PathVariable("id") UUID id) {
    return FinancialRecordApiResponse.from(records.findById(new FinancialRecordId(id)));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAuthority('mvf.cancelar')")
  FinancialRecordApiResponse cancel(
      @PathVariable("id") UUID id,
      @RequestBody @Valid CancelFinancialRecordApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return FinancialRecordApiResponse.from(
        records.cancel(
            new CancelFinancialRecordCommand(
                new FinancialRecordId(id), body.reason(), principal.userId())));
  }

  @PostMapping("/{id}/renegotiate")
  @PreAuthorize("hasAuthority('mvf.renegociar')")
  FinancialRecordApiResponse renegotiate(
      @PathVariable("id") UUID id,
      @RequestBody @Valid RenegotiateFinancialRecordApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return FinancialRecordApiResponse.from(
        records.renegotiate(body.toCommand(id, principal.userId())));
  }
}
