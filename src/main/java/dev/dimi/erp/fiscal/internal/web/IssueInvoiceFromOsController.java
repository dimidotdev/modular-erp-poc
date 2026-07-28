package dev.dimi.erp.fiscal.internal.web;

import dev.dimi.erp.fiscal.internal.application.IssueInvoiceFromOsService;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.ordemservico.OsBillingType;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoint para faturamento de OS. Segue o contrato {@code from-sale/{saleId}}: fiscal é dono da
 * orquestração porque emite as invoices, e OS recebe a conclusão via {@link
 * dev.dimi.erp.ordemservico.OsBillingLink}.
 */
@RestController
@RequestMapping("/api/v1/fiscal/invoices")
@RequiredArgsConstructor
class IssueInvoiceFromOsController {

  private final IssueInvoiceFromOsService service;

  @PostMapping("/from-os/{osId}")
  @PreAuthorize("hasAuthority('os.faturar')")
  public ResponseEntity<IssueFromOsResponse> issueFromOs(
      @PathVariable("osId") UUID osId,
      @RequestBody(required = false) @Valid IssueFromOsRequest body,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    IssueInvoiceFromOsService.Result result =
        service.issueBillingFromOs(
            new IssueInvoiceFromOsService.IssueOsBillingCommand(
                new ServiceOrderId(osId),
                body == null ? null : body.billingType(),
                body == null ? null : body.notes(),
                principal.userId(),
                idempotencyKey));
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(IssueFromOsResponse.from(result));
  }

  public record IssueFromOsRequest(OsBillingType billingType, String notes) {}

  public record IssueFromOsResponse(UUID billingId, UUID nfeInvoiceId, UUID nfseInvoiceId) {
    static IssueFromOsResponse from(IssueInvoiceFromOsService.Result r) {
      return new IssueFromOsResponse(
          r.billingId().value(),
          r.nfeInvoiceId() == null ? null : r.nfeInvoiceId().value(),
          r.nfseInvoiceId() == null ? null : r.nfseInvoiceId().value());
    }
  }
}
