package dev.dimi.erp.movimentacao.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleReturnId;
import dev.dimi.erp.movimentacao.internal.application.ProcessReturnService;
import dev.dimi.erp.movimentacao.internal.web.dto.OpenSaleReturnApiRequest;
import dev.dimi.erp.movimentacao.internal.web.dto.SaleReturnApiResponse;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code sale_return} ({@code /api/v1/movimentacao/returns/**}). NFe de devolução
 * e estorno de MVF ficam para slices fiscal/financeiro.
 */
@RestController
@RequestMapping("/api/v1/movimentacao/returns")
@RequiredArgsConstructor
class SaleReturnController {

  private final ProcessReturnService returns;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('devolucao.criar')")
  SaleReturnApiResponse open(
      @RequestBody @Valid OpenSaleReturnApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return SaleReturnApiResponse.from(returns.open(body.toCommand(principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('devolucao.consultar')")
  SaleReturnApiResponse findById(@PathVariable("id") UUID id) {
    return SaleReturnApiResponse.from(returns.findById(new SaleReturnId(id)));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('devolucao.consultar')")
  List<SaleReturnApiResponse> listBySale(@RequestParam("saleId") UUID saleId) {
    return returns.listBySale(new SaleId(saleId)).stream()
        .map(SaleReturnApiResponse::from)
        .toList();
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('devolucao.fechar')")
  SaleReturnApiResponse close(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return SaleReturnApiResponse.from(returns.close(new SaleReturnId(id), principal.userId()));
  }
}
