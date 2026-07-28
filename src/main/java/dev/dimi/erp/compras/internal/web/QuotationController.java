package dev.dimi.erp.compras.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.QuotationStatus;
import dev.dimi.erp.compras.internal.application.QuotationService;
import dev.dimi.erp.compras.internal.web.dto.InviteSupplierApiRequest;
import dev.dimi.erp.compras.internal.web.dto.OpenQuotationApiRequest;
import dev.dimi.erp.compras.internal.web.dto.QuotationApiResponse;
import dev.dimi.erp.compras.internal.web.dto.RegisterQuotationResponseApiRequest;
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

/** Endpoints REST de {@code quotation} ({@code /api/v1/compras/quotations/**}). */
@RestController
@RequestMapping("/api/v1/compras/quotations")
@RequiredArgsConstructor
class QuotationController {

  private static final int MAX_PAGE_SIZE = 100;

  private final QuotationService quotations;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('compra.cotar')")
  QuotationApiResponse open(
      @RequestBody @Valid OpenQuotationApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return QuotationApiResponse.from(quotations.open(body.toCommand(principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('compra.consultar')")
  QuotationApiResponse findById(@PathVariable("id") UUID id) {
    return QuotationApiResponse.from(quotations.findById(new QuotationId(id)));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('compra.consultar')")
  org.springframework.data.domain.Page<QuotationApiResponse> list(
      @RequestParam("branchId") UUID branchId,
      @RequestParam(name = "status", defaultValue = "OPEN") QuotationStatus status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    return quotations
        .listByBranchAndStatus(new BranchId(branchId), status, pageable)
        .map(QuotationApiResponse::from);
  }

  @PostMapping("/{id}/suppliers")
  @PreAuthorize("hasAuthority('compra.cotar')")
  QuotationApiResponse invite(
      @PathVariable("id") UUID id,
      @RequestBody @Valid InviteSupplierApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return QuotationApiResponse.from(quotations.invite(body.toCommand(id, principal.userId())));
  }

  @PostMapping("/{id}/responses")
  @PreAuthorize("hasAuthority('compra.cotar')")
  QuotationApiResponse registerResponse(
      @PathVariable("id") UUID id,
      @RequestBody @Valid RegisterQuotationResponseApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return QuotationApiResponse.from(
        quotations.registerResponse(body.toCommand(id, principal.userId())));
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('compra.cotar')")
  QuotationApiResponse close(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return QuotationApiResponse.from(quotations.close(new QuotationId(id), principal.userId()));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAuthority('compra.cotar')")
  QuotationApiResponse cancel(@PathVariable("id") UUID id) {
    return QuotationApiResponse.from(quotations.cancel(new QuotationId(id)));
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
