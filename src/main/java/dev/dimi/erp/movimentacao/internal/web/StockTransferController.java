package dev.dimi.erp.movimentacao.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.movimentacao.StockTransferId;
import dev.dimi.erp.movimentacao.internal.application.ExecuteTransferService;
import dev.dimi.erp.movimentacao.internal.web.dto.OpenStockTransferApiRequest;
import dev.dimi.erp.movimentacao.internal.web.dto.StockTransferApiResponse;
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
 * Endpoints REST de {@code stock_transfer} ({@code /api/v1/movimentacao/transfers/**}). Par atômico
 * OUT/IN é aplicado no {@code close} via {@code StockInventoryPort.transfer}.
 */
@RestController
@RequestMapping("/api/v1/movimentacao/transfers")
@RequiredArgsConstructor
class StockTransferController {

  private final ExecuteTransferService transfers;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('transferencia.criar')")
  StockTransferApiResponse open(
      @RequestBody @Valid OpenStockTransferApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return StockTransferApiResponse.from(transfers.open(body.toCommand(principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('transferencia.consultar')")
  StockTransferApiResponse findById(@PathVariable("id") UUID id) {
    return StockTransferApiResponse.from(transfers.findById(new StockTransferId(id)));
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('transferencia.fechar')")
  StockTransferApiResponse close(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return StockTransferApiResponse.from(
        transfers.close(new StockTransferId(id), principal.userId()));
  }
}
