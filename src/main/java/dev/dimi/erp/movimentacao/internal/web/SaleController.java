package dev.dimi.erp.movimentacao.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleStatus;
import dev.dimi.erp.movimentacao.internal.application.InvalidSaleOperationException;
import dev.dimi.erp.movimentacao.internal.application.SaleService;
import dev.dimi.erp.movimentacao.internal.web.dto.OpenSaleApiRequest;
import dev.dimi.erp.movimentacao.internal.web.dto.SaleApiResponse;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code sale} ({@code /api/v1/movimentacao/sales/**}). RN-MOV-004: {@code POST
 * /sales} exige {@code Idempotency-Key} no header (slice 5.2 valida obrigatoriedade).
 */
@RestController
@RequestMapping("/api/v1/movimentacao/sales")
@RequiredArgsConstructor
class SaleController {

  private static final int MAX_PAGE_SIZE = 100;

  private final SaleService sales;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('venda.criar')")
  SaleApiResponse open(
      @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
      @RequestBody @Valid OpenSaleApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    if (idempotencyKey == null || idempotencyKey.isBlank()) {
      throw new InvalidSaleOperationException(
          "idempotency-key-required",
          "Header Idempotency-Key é obrigatório em POST /sales (RN-MOV-004).");
    }
    return SaleApiResponse.from(sales.open(body.toCommand(idempotencyKey, principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('venda.consultar')")
  SaleApiResponse findById(@PathVariable("id") UUID id) {
    return SaleApiResponse.from(sales.findById(new SaleId(id)));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('venda.consultar')")
  org.springframework.data.domain.Page<SaleApiResponse> list(
      @RequestParam("branchId") UUID branchId,
      @RequestParam(name = "status", defaultValue = "OPEN") SaleStatus status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    return sales
        .listByBranchAndStatus(new BranchId(branchId), status, pageable)
        .map(SaleApiResponse::from);
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('venda.fechar')")
  SaleApiResponse close(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return SaleApiResponse.from(sales.close(new SaleId(id), principal.userId()));
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
