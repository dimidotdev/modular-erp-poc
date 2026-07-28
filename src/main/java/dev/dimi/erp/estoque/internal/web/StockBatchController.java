package dev.dimi.erp.estoque.internal.web;

import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.internal.application.ManageStockBatchService;
import dev.dimi.erp.estoque.internal.web.dto.CreateStockBatchApiRequest;
import dev.dimi.erp.estoque.internal.web.dto.StockBatchApiResponse;
import dev.dimi.erp.estoque.internal.web.dto.UpdateStockBatchApiRequest;
import dev.dimi.erp.produto.PackagingId;
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

/** Endpoints REST de {@code stock_batch} ({@code /api/v1/estoque/batches/**}). */
@RestController
@RequestMapping("/api/v1/estoque/batches")
@RequiredArgsConstructor
class StockBatchController {

  private final ManageStockBatchService batches;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('estoque.editar')")
  StockBatchApiResponse register(@RequestBody @Valid CreateStockBatchApiRequest body) {
    return StockBatchApiResponse.from(
        batches.register(
            new PackagingId(body.packagingId()),
            body.code(),
            body.manufacturedOn(),
            body.expiresOn(),
            body.notes()));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('estoque.consultar')")
  List<StockBatchApiResponse> list(@RequestParam("packagingId") UUID packagingId) {
    return batches.listByPackaging(new PackagingId(packagingId)).stream()
        .map(StockBatchApiResponse::from)
        .toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('estoque.consultar')")
  StockBatchApiResponse findById(@PathVariable("id") UUID id) {
    return StockBatchApiResponse.from(batches.findById(new StockBatchId(id)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('estoque.editar')")
  StockBatchApiResponse update(
      @PathVariable("id") UUID id, @RequestBody @Valid UpdateStockBatchApiRequest body) {
    return StockBatchApiResponse.from(
        batches.update(
            new StockBatchId(id), body.manufacturedOn(), body.expiresOn(), body.notes()));
  }
}
