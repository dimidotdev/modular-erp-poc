package dev.dimi.erp.estoque.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.internal.application.ManageStockLocationService;
import dev.dimi.erp.estoque.internal.web.dto.CreateStockLocationApiRequest;
import dev.dimi.erp.estoque.internal.web.dto.RenameStockLocationApiRequest;
import dev.dimi.erp.estoque.internal.web.dto.StockLocationApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Endpoints REST de {@code stock_location} ({@code /api/v1/estoque/locations/**}). */
@RestController
@RequestMapping("/api/v1/estoque/locations")
@RequiredArgsConstructor
class StockLocationController {

  private final ManageStockLocationService locations;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('estoque.editar')")
  StockLocationApiResponse register(@RequestBody @Valid CreateStockLocationApiRequest body) {
    return StockLocationApiResponse.from(
        locations.register(
            new BranchId(body.branchId()),
            body.code(),
            body.name(),
            Boolean.TRUE.equals(body.asDefault())));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('estoque.consultar')")
  List<StockLocationApiResponse> list(@RequestParam("branchId") UUID branchId) {
    return locations.listByBranch(new BranchId(branchId)).stream()
        .map(StockLocationApiResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('estoque.consultar')")
  StockLocationApiResponse findById(@PathVariable("id") UUID id) {
    return StockLocationApiResponse.from(locations.findById(new StockLocationId(id)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('estoque.editar')")
  StockLocationApiResponse rename(
      @PathVariable("id") UUID id, @RequestBody @Valid RenameStockLocationApiRequest body) {
    return StockLocationApiResponse.from(locations.rename(new StockLocationId(id), body.name()));
  }

  @PatchMapping("/{id}/padrao")
  @PreAuthorize("hasAuthority('estoque.editar')")
  StockLocationApiResponse markDefault(@PathVariable("id") UUID id) {
    return StockLocationApiResponse.from(locations.markDefault(new StockLocationId(id)));
  }

  @PatchMapping("/{id}/desativar")
  @PreAuthorize("hasAuthority('estoque.editar')")
  StockLocationApiResponse deactivate(@PathVariable("id") UUID id) {
    return StockLocationApiResponse.from(locations.deactivate(new StockLocationId(id)));
  }

  @PatchMapping("/{id}/reativar")
  @PreAuthorize("hasAuthority('estoque.editar')")
  StockLocationApiResponse reactivate(@PathVariable("id") UUID id) {
    return StockLocationApiResponse.from(locations.reactivate(new StockLocationId(id)));
  }
}
