package dev.dimi.erp.movimentacao.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SalesOrderId;
import dev.dimi.erp.movimentacao.SalesOrderStatus;
import dev.dimi.erp.movimentacao.internal.application.ManageSalesOrderService;
import dev.dimi.erp.movimentacao.internal.application.SaleService;
import dev.dimi.erp.movimentacao.internal.web.dto.CreateSalesOrderApiRequest;
import dev.dimi.erp.movimentacao.internal.web.dto.SaleApiResponse;
import dev.dimi.erp.movimentacao.internal.web.dto.SalesOrderApiResponse;
import dev.dimi.erp.movimentacao.internal.web.dto.UpdateSalesOrderApiRequest;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code sales_order} ({@code /api/v1/movimentacao/sales-orders/**}). Permissões
 * por ação conforme {@code docs/domain/14-movimentacao.md}.
 */
@RestController
@RequestMapping("/api/v1/movimentacao/sales-orders")
@RequiredArgsConstructor
class SalesOrderController {

  private static final int MAX_PAGE_SIZE = 100;

  private final ManageSalesOrderService orders;
  private final SaleService sales;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('pedido.criar')")
  SalesOrderApiResponse register(
      @RequestBody @Valid CreateSalesOrderApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return SalesOrderApiResponse.from(orders.register(body.toCommand(principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('pedido.consultar')")
  SalesOrderApiResponse findById(@PathVariable("id") UUID id) {
    return SalesOrderApiResponse.from(orders.findById(new SalesOrderId(id)));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('pedido.consultar')")
  org.springframework.data.domain.Page<SalesOrderApiResponse> list(
      @RequestParam("branchId") UUID branchId,
      @RequestParam(name = "status", defaultValue = "DRAFT") SalesOrderStatus status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    return orders
        .listByBranchAndStatus(new BranchId(branchId), status, pageable)
        .map(SalesOrderApiResponse::from);
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('pedido.editar')")
  SalesOrderApiResponse update(
      @PathVariable("id") UUID id,
      @RequestBody @Valid UpdateSalesOrderApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return SalesOrderApiResponse.from(
        orders.update(body.toCommand(new SalesOrderId(id), principal.userId())));
  }

  @PostMapping("/{id}/submit")
  @PreAuthorize("hasAuthority('pedido.criar')")
  SalesOrderApiResponse submit(@PathVariable("id") UUID id) {
    return SalesOrderApiResponse.from(orders.submit(new SalesOrderId(id)));
  }

  @PostMapping("/{id}/approve")
  @PreAuthorize("hasAuthority('pedido.aprovar')")
  SalesOrderApiResponse approve(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return SalesOrderApiResponse.from(orders.approve(new SalesOrderId(id), principal.userId()));
  }

  @PostMapping("/{id}/reject")
  @PreAuthorize("hasAuthority('pedido.aprovar')")
  SalesOrderApiResponse reject(@PathVariable("id") UUID id) {
    return SalesOrderApiResponse.from(orders.reject(new SalesOrderId(id)));
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAuthority('pedido.editar')")
  SalesOrderApiResponse cancel(@PathVariable("id") UUID id) {
    return SalesOrderApiResponse.from(orders.cancel(new SalesOrderId(id)));
  }

  /**
   * Converte pedido APPROVED em venda nova OPEN. RN-MOV-002. Permissão {@code venda.criar} —
   * conversão cria documento de saída.
   */
  @PostMapping("/{id}/convert")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('venda.criar')")
  SaleApiResponse convert(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return SaleApiResponse.from(sales.convertFromOrder(new SalesOrderId(id), principal.userId()));
  }

  /** Helper opcional caso o caller queira saber o id da venda gerada. */
  @SuppressWarnings("unused")
  private static SaleId asSaleId(UUID id) {
    return new SaleId(id);
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
