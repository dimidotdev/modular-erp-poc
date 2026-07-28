package dev.dimi.erp.fiscal.internal.web;

import dev.dimi.erp.fiscal.InvoiceId;
import dev.dimi.erp.fiscal.internal.application.InvoiceLookupService;
import dev.dimi.erp.fiscal.internal.application.IssueInvoiceFromSaleService;
import dev.dimi.erp.fiscal.internal.web.dto.InvoiceApiResponse;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.movimentacao.SaleId;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code invoice} ({@code /api/v1/fiscal/invoices/**}).
 *
 * <p>Expõe emissão a partir de venda fechada e consulta por identificador. Outras operações fiscais
 * usam endpoints dedicados quando disponíveis.
 */
@RestController
@RequestMapping("/api/v1/fiscal/invoices")
@RequiredArgsConstructor
class InvoiceController {

  private final IssueInvoiceFromSaleService issueFromSaleService;
  private final InvoiceLookupService lookupService;

  /**
   * Emite NFe a partir de venda fechada. RN-FIS-007 idempotência por origem + RN-FIS-008
   * idempotência por header. Quando o invoice já existe (autorizado ou rejeitado previamente),
   * retorna {@code 200 OK} sem efeitos colaterais; emissão nova devolve {@code 201 CREATED}.
   */
  @PostMapping("/from-sale/{saleId}")
  @PreAuthorize("hasAuthority('nfe.emitir')")
  public ResponseEntity<InvoiceApiResponse> emitFromSale(
      @PathVariable("saleId") UUID saleId,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    IssueInvoiceFromSaleService.Result result =
        issueFromSaleService.issueFromSale(new SaleId(saleId), principal.userId(), idempotencyKey);
    HttpStatus status = result.created() ? HttpStatus.CREATED : HttpStatus.OK;
    return ResponseEntity.status(status).body(InvoiceApiResponse.from(result.invoice()));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('nfe.consultar')")
  public InvoiceApiResponse findById(@PathVariable("id") UUID id) {
    return InvoiceApiResponse.from(lookupService.findById(new InvoiceId(id)));
  }
}
