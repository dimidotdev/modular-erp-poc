package dev.dimi.erp.produto.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceLookupPort;
import dev.dimi.erp.produto.PriceTableId;
import dev.dimi.erp.produto.internal.application.ManagePriceTableService;
import dev.dimi.erp.produto.internal.web.dto.CreatePriceTableApiRequest;
import dev.dimi.erp.produto.internal.web.dto.PriceQuoteApiResponse;
import dev.dimi.erp.produto.internal.web.dto.PriceTableApiResponse;
import dev.dimi.erp.produto.internal.web.dto.PriceTableItemApiResponse;
import dev.dimi.erp.produto.internal.web.dto.UpdatePriceTableApiRequest;
import dev.dimi.erp.produto.internal.web.dto.UpsertPriceItemsApiRequest;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code price_table} ({@code /api/v1/produtos/price-tables/**}) e lookup de
 * preço autoritativo ({@code /api/v1/produtos/prices/quote}). Permissions:
 *
 * <ul>
 *   <li>{@code produto.preco.consultar} — listar, buscar, listar items, quote.
 *   <li>{@code produto.preco.alterar} — criar/atualizar tabela, batch upsert items,
 *       desativar/reativar.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/produtos")
@RequiredArgsConstructor
class PriceTableController {

  private final ManagePriceTableService prices;
  private final PriceLookupPort priceLookup;

  // ---------------------------------------------------------------------------
  // PriceTable CRUD
  // ---------------------------------------------------------------------------

  @PostMapping("/price-tables")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('produto.preco.alterar')")
  PriceTableApiResponse register(
      @RequestBody @Valid CreatePriceTableApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PriceTableApiResponse.from(prices.register(body.toCommand(principal.userId())));
  }

  @GetMapping("/price-tables")
  @PreAuthorize("hasAuthority('produto.preco.consultar')")
  List<PriceTableApiResponse> list(
      @RequestParam(name = "branchId", required = false) UUID branchId) {
    if (branchId != null) {
      return prices.listByBranch(new BranchId(branchId)).stream()
          .map(PriceTableApiResponse::from)
          .toList();
    }
    return prices.list().stream().map(PriceTableApiResponse::from).toList();
  }

  @GetMapping("/price-tables/{id}")
  @PreAuthorize("hasAuthority('produto.preco.consultar')")
  PriceTableApiResponse findById(@PathVariable("id") UUID id) {
    return PriceTableApiResponse.from(prices.findById(new PriceTableId(id)));
  }

  @PatchMapping("/price-tables/{id}")
  @PreAuthorize("hasAuthority('produto.preco.alterar')")
  PriceTableApiResponse update(
      @PathVariable("id") UUID id,
      @RequestBody @Valid UpdatePriceTableApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PriceTableApiResponse.from(
        prices.update(body.toCommand(new PriceTableId(id), principal.userId())));
  }

  @PatchMapping("/price-tables/{id}/desativar")
  @PreAuthorize("hasAuthority('produto.preco.alterar')")
  PriceTableApiResponse deactivate(@PathVariable("id") UUID id) {
    return PriceTableApiResponse.from(prices.deactivate(new PriceTableId(id)));
  }

  @PatchMapping("/price-tables/{id}/reativar")
  @PreAuthorize("hasAuthority('produto.preco.alterar')")
  PriceTableApiResponse reactivate(@PathVariable("id") UUID id) {
    return PriceTableApiResponse.from(prices.reactivate(new PriceTableId(id)));
  }

  // ---------------------------------------------------------------------------
  // PriceTableItem batch upsert
  // ---------------------------------------------------------------------------

  @PutMapping("/price-tables/{id}/items")
  @PreAuthorize("hasAuthority('produto.preco.alterar')")
  List<PriceTableItemApiResponse> upsertItems(
      @PathVariable("id") UUID id,
      @RequestBody @Valid UpsertPriceItemsApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return prices.upsertItems(body.toCommand(new PriceTableId(id), principal.userId())).stream()
        .map(PriceTableItemApiResponse::from)
        .toList();
  }

  @GetMapping("/price-tables/{id}/items")
  @PreAuthorize("hasAuthority('produto.preco.consultar')")
  List<PriceTableItemApiResponse> listItems(@PathVariable("id") UUID id) {
    return prices.listItems(new PriceTableId(id)).stream()
        .map(PriceTableItemApiResponse::from)
        .toList();
  }

  // ---------------------------------------------------------------------------
  // Lookup autoritativo (RN-PRD-004)
  // ---------------------------------------------------------------------------

  @GetMapping("/prices/quote")
  @PreAuthorize("hasAuthority('produto.preco.consultar')")
  PriceQuoteApiResponse quote(
      @RequestParam("packagingId") UUID packagingId,
      @RequestParam("branchId") UUID branchId,
      @RequestParam(name = "date", required = false) LocalDate date) {
    LocalDate when = date == null ? LocalDate.now() : date;
    return priceLookup
        .resolve(new PackagingId(packagingId), new BranchId(branchId), when)
        .map(PriceQuoteApiResponse::from)
        .orElseThrow(
            () ->
                new dev.dimi.erp.produto.internal.application.InvalidProductDataException(
                    "no-price",
                    "Não há tabela de preço ativa cobrindo packaging+branch+date informados."));
  }
}
