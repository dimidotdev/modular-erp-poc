package dev.dimi.erp.movimentacao.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import dev.dimi.erp.movimentacao.InventoryAdjustmentId;
import dev.dimi.erp.movimentacao.internal.application.RecordInventoryAdjustmentService;
import dev.dimi.erp.movimentacao.internal.web.dto.InventoryAdjustmentApiResponse;
import dev.dimi.erp.movimentacao.internal.web.dto.RecordInventoryAdjustmentApiRequest;
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
 * Endpoints REST de {@code stock_inventory_adjustment} ({@code
 * /api/v1/movimentacao/inventory-adjustments/**}). RN-MOV-025: registro exige step-up + permission
 * {@code estoque.ajustar} + justificativa.
 */
@RestController
@RequestMapping("/api/v1/movimentacao/inventory-adjustments")
@RequiredArgsConstructor
class InventoryAdjustmentController {

  private final RecordInventoryAdjustmentService adjustments;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('estoque.ajustar')")
  InventoryAdjustmentApiResponse record(
      @RequestBody @Valid RecordInventoryAdjustmentApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return InventoryAdjustmentApiResponse.from(
        adjustments.record(body.toCommand(principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('estoque.consultar')")
  InventoryAdjustmentApiResponse findById(@PathVariable("id") UUID id) {
    return InventoryAdjustmentApiResponse.from(adjustments.findById(new InventoryAdjustmentId(id)));
  }
}
