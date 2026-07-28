package dev.dimi.erp.estoque.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.estoque.MovementId;
import dev.dimi.erp.estoque.StockBatchId;
import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockQueryPort;
import dev.dimi.erp.estoque.internal.application.MovementNotFoundException;
import dev.dimi.erp.estoque.internal.persistence.StockMovementRepository;
import dev.dimi.erp.estoque.internal.web.dto.AdjustStockApiRequest;
import dev.dimi.erp.estoque.internal.web.dto.ReverseStockApiRequest;
import dev.dimi.erp.estoque.internal.web.dto.StockMovementApiResponse;
import dev.dimi.erp.estoque.internal.web.dto.StockSnapshotApiResponse;
import dev.dimi.erp.estoque.internal.web.dto.TransferResultApiResponse;
import dev.dimi.erp.estoque.internal.web.dto.TransferStockApiRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import dev.dimi.erp.produto.PackagingId;
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
 * Endpoints REST principais de estoque ({@code /api/v1/estoque/**}). Operações sensíveis
 * (adjustment, reverse) exigem step-up (RN-EST-010). Transferência exige permission própria.
 */
@RestController
@RequestMapping("/api/v1/estoque")
@RequiredArgsConstructor
class StockController {

  private final StockInventoryPort inventory;
  private final StockQueryPort queries;
  private final StockMovementRepository movements;
  private final StepUpPolicy stepUpPolicy;

  // ---------------------------------------------------------------------------
  // Snapshots e ledger (read-only)
  // ---------------------------------------------------------------------------

  @GetMapping("/snapshots")
  @PreAuthorize("hasAuthority('estoque.consultar')")
  List<StockSnapshotApiResponse> snapshots(
      @RequestParam("packagingId") UUID packagingId, @RequestParam("branchId") UUID branchId) {
    return queries.snapshotsFor(new PackagingId(packagingId), new BranchId(branchId)).stream()
        .map(StockSnapshotApiResponse::from)
        .toList();
  }

  @GetMapping("/movements/{id}")
  @PreAuthorize("hasAuthority('estoque.consultar')")
  StockMovementApiResponse findMovementById(@PathVariable("id") UUID id) {
    return StockMovementApiResponse.from(
        movements
            .findById(id)
            .orElseThrow(() -> new MovementNotFoundException(new MovementId(id))));
  }

  // ---------------------------------------------------------------------------
  // Operações sensíveis: ADJUSTMENT (+ step-up)
  // ---------------------------------------------------------------------------

  @PostMapping("/adjustments")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('estoque.ajustar')")
  StockMovementApiResponse adjust(
      @RequestBody @Valid AdjustStockApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    MovementId movementId =
        inventory.adjust(
            new StockInventoryPort.AdjustCommand(
                new PackagingId(body.packagingId()),
                new BranchId(body.branchId()),
                new StockLocationId(body.locationId()),
                body.batchId() == null ? null : new StockBatchId(body.batchId()),
                body.delta(),
                body.reason(),
                principal.userId()));
    return StockMovementApiResponse.from(
        movements
            .findById(movementId.value())
            .orElseThrow(() -> new MovementNotFoundException(movementId)));
  }

  // ---------------------------------------------------------------------------
  // TRANSFER (não sensível, permission própria)
  // ---------------------------------------------------------------------------

  @PostMapping("/transfers")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('estoque.transferir')")
  TransferResultApiResponse transfer(
      @RequestBody @Valid TransferStockApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    StockInventoryPort.TransferResult result =
        inventory.transfer(
            new StockInventoryPort.TransferCommand(
                new PackagingId(body.packagingId()),
                new BranchId(body.sourceBranchId()),
                new StockLocationId(body.sourceLocationId()),
                new BranchId(body.destBranchId()),
                new StockLocationId(body.destLocationId()),
                body.batchId() == null ? null : new StockBatchId(body.batchId()),
                body.quantity(),
                UUID.randomUUID(),
                body.reason(),
                principal.userId()));
    return TransferResultApiResponse.from(result);
  }

  // ---------------------------------------------------------------------------
  // REVERSE (+ step-up)
  // ---------------------------------------------------------------------------

  @PostMapping("/movements/{id}/reverse")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('estoque.ajustar')")
  StockMovementApiResponse reverse(
      @PathVariable("id") UUID id,
      @RequestBody @Valid ReverseStockApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    MovementId reversalId =
        inventory.reverse(
            new StockInventoryPort.ReverseCommand(
                new MovementId(id), body.reason(), principal.userId()));
    return StockMovementApiResponse.from(
        movements
            .findById(reversalId.value())
            .orElseThrow(() -> new MovementNotFoundException(reversalId)));
  }
}
