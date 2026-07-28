package dev.dimi.erp.ordemservico.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.ordemservico.BillingId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.internal.application.CancelOsBillingService;
import dev.dimi.erp.ordemservico.internal.application.ServiceOrderQueryService;
import dev.dimi.erp.ordemservico.internal.web.dto.BillingApiResponse;
import dev.dimi.erp.ordemservico.internal.web.dto.CancelBillingRequest;
import jakarta.validation.Valid;
import java.util.List;
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
 * Endpoints REST do lifecycle do {@code service_order_billing} ({@code
 * /api/v1/ordens-servico/{id}/billings/...}). Cancelamento e listagem de faturamentos emitidos da
 * OS.
 *
 * <p><b>Emissão</b> da NFe+NFSe a partir de uma OS NÃO mora aqui — foi movida para o módulo fiscal,
 * em {@code POST /api/v1/fiscal/invoices/from-os/{osId}}, mantendo o módulo {@code ordemservico}
 * desacoplado de {@code fiscal}. O fiscal lê a OS via {@code OsQueryPort} e devolve o link via
 * {@code OsBillingLink} (síncrono na mesma tx, mesmo padrão de {@code /from-sale/{saleId}}).
 *
 * <p>Controller separado de {@link ServiceOrderController} para manter responsabilidades distintas:
 * o controller de OS lida com lifecycle/transições, este lida com lifecycle do {@code
 * service_order_billing} (cancelamento + listagem).
 */
@RestController
@RequestMapping("/api/v1/ordens-servico")
@RequiredArgsConstructor
class OsBillingController {

  private final CancelOsBillingService cancelBillingService;
  private final ServiceOrderQueryService queryService;

  @PostMapping("/{id}/billings/{billingId}/cancel")
  @PreAuthorize("hasAuthority('os.cancelar')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void cancelBilling(
      @PathVariable("id") UUID id,
      @PathVariable("billingId") UUID billingId,
      @Valid @RequestBody CancelBillingRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    cancelBillingService.cancel(new BillingId(billingId), body.reason(), principal.userId());
  }

  @GetMapping("/{id}/billings")
  @PreAuthorize("hasAuthority('os.consultar')")
  public List<BillingApiResponse> listBillings(@PathVariable("id") UUID id) {
    return queryService.findBillings(new ServiceOrderId(id)).stream()
        .map(BillingApiResponse::from)
        .toList();
  }
}
