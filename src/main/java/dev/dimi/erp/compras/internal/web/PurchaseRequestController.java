package dev.dimi.erp.compras.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.PurchaseRequestStatus;
import dev.dimi.erp.compras.internal.application.ManagePurchaseRequestService;
import dev.dimi.erp.compras.internal.web.dto.CreatePurchaseRequestApiRequest;
import dev.dimi.erp.compras.internal.web.dto.PurchaseRequestApiResponse;
import dev.dimi.erp.compras.internal.web.dto.RejectPurchaseRequestApiRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code purchase_request} ({@code /api/v1/compras/requests/**}). Permissões por
 * ação conforme {@code docs/domain/15-compras.md}.
 */
@RestController
@RequestMapping("/api/v1/compras/requests")
@RequiredArgsConstructor
class PurchaseRequestController {

  private static final int MAX_PAGE_SIZE = 100;

  private final ManagePurchaseRequestService requests;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('compra.solicitar')")
  PurchaseRequestApiResponse register(
      @RequestBody @Valid CreatePurchaseRequestApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PurchaseRequestApiResponse.from(requests.register(body.toCommand(principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('compra.consultar')")
  PurchaseRequestApiResponse findById(@PathVariable("id") UUID id) {
    return PurchaseRequestApiResponse.from(requests.findById(new PurchaseRequestId(id)));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('compra.consultar')")
  org.springframework.data.domain.Page<PurchaseRequestApiResponse> list(
      @RequestParam("branchId") UUID branchId,
      @RequestParam(name = "status", defaultValue = "DRAFT") PurchaseRequestStatus status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    return requests
        .listByBranchAndStatus(new BranchId(branchId), status, pageable)
        .map(PurchaseRequestApiResponse::from);
  }

  @PostMapping("/{id}/submit")
  @PreAuthorize("hasAuthority('compra.solicitar')")
  PurchaseRequestApiResponse submit(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return PurchaseRequestApiResponse.from(
        requests.submit(new PurchaseRequestId(id), principal.userId()));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAuthority('compra.aprovar')")
  PurchaseRequestApiResponse approve(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return PurchaseRequestApiResponse.from(
        requests.approve(new PurchaseRequestId(id), principal.userId()));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAuthority('compra.aprovar')")
  PurchaseRequestApiResponse reject(
      @PathVariable("id") UUID id,
      @RequestBody @Valid RejectPurchaseRequestApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PurchaseRequestApiResponse.from(
        requests.reject(new PurchaseRequestId(id), body.reason(), principal.userId()));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAuthority('compra.solicitar')")
  PurchaseRequestApiResponse cancel(@PathVariable("id") UUID id) {
    return PurchaseRequestApiResponse.from(requests.cancel(new PurchaseRequestId(id)));
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
