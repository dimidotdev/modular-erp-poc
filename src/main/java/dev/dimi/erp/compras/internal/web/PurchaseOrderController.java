package dev.dimi.erp.compras.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseOrderId;
import dev.dimi.erp.compras.PurchaseOrderStatus;
import dev.dimi.erp.compras.internal.application.PurchaseOrderService;
import dev.dimi.erp.compras.internal.application.ReceivePurchaseOrderService;
import dev.dimi.erp.compras.internal.web.dto.CancelPurchaseOrderApiRequest;
import dev.dimi.erp.compras.internal.web.dto.GenerateOrdersFromQuotationApiRequest;
import dev.dimi.erp.compras.internal.web.dto.OpenPurchaseOrderApiRequest;
import dev.dimi.erp.compras.internal.web.dto.PurchaseOrderApiResponse;
import dev.dimi.erp.compras.internal.web.dto.PurchaseReceiptApiResponse;
import dev.dimi.erp.compras.internal.web.dto.ReceivePurchaseOrderApiRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import jakarta.validation.Valid;
import java.util.List;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints REST de {@code purchase_order} ({@code /api/v1/compras/orders/**}). */
@RestController
@RequestMapping("/api/v1/compras/orders")
@RequiredArgsConstructor
class PurchaseOrderController {

  private static final int MAX_PAGE_SIZE = 100;

  private final PurchaseOrderService orders;
  private final ReceivePurchaseOrderService receipts;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('compra.pedir')")
  PurchaseOrderApiResponse openAdHoc(
      @RequestBody @Valid OpenPurchaseOrderApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PurchaseOrderApiResponse.from(orders.openAdHoc(body.toCommand(principal.userId())));
  }

  @PostMapping("/from-quotation/{quotationId}")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('compra.pedir')")
  List<PurchaseOrderApiResponse> generateFromQuotation(
      @PathVariable("quotationId") UUID quotationId,
      @RequestBody @Valid GenerateOrdersFromQuotationApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return orders.generateFromQuotation(body.toCommand(quotationId, principal.userId())).stream()
        .map(PurchaseOrderApiResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('compra.consultar')")
  PurchaseOrderApiResponse findById(@PathVariable("id") UUID id) {
    return PurchaseOrderApiResponse.from(orders.findById(new PurchaseOrderId(id)));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('compra.consultar')")
  org.springframework.data.domain.Page<PurchaseOrderApiResponse> list(
      @RequestParam("branchId") UUID branchId,
      @RequestParam(name = "status", defaultValue = "OPEN") PurchaseOrderStatus status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    return orders
        .listByBranchAndStatus(new BranchId(branchId), status, pageable)
        .map(PurchaseOrderApiResponse::from);
  }

  @PostMapping("/{id}/send")
  @PreAuthorize("hasAuthority('compra.pedir')")
  PurchaseOrderApiResponse send(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return PurchaseOrderApiResponse.from(orders.send(new PurchaseOrderId(id), principal.userId()));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAuthority('compra.cancelar')")
  PurchaseOrderApiResponse cancel(
      @PathVariable("id") UUID id,
      @RequestBody @Valid CancelPurchaseOrderApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return PurchaseOrderApiResponse.from(
        orders.cancel(new PurchaseOrderId(id), body.reason(), principal.userId()));
  }

  @PostMapping("/{id}/receive")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('compra.receber')")
  PurchaseReceiptApiResponse receive(
      @PathVariable("id") UUID id,
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid ReceivePurchaseOrderApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new dev.dimi.erp.compras.internal.application.InvalidPurchaseOperationException(
          "idempotency-key-required",
          "Header Idempotency-Key é obrigatório em POST /receive (RN-COM-014).");
    }
    if (body.allowExcess() && !principal.hasPermission("compra.receber.excesso")) {
      throw new dev.dimi.erp.compras.internal.application.InvalidPurchaseOperationException(
          "excess-permission-required",
          "allowExcess=true exige permission compra.receber.excesso (RN-COM-015).");
    }
    return PurchaseReceiptApiResponse.from(
        receipts.receive(body.toCommand(id, idempotencyKey, principal.userId())));
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
