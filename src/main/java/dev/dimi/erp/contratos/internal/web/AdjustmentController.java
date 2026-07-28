package dev.dimi.erp.contratos.internal.web;

import dev.dimi.erp.contratos.AdjustmentId;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.internal.application.AdjustContractService;
import dev.dimi.erp.contratos.internal.application.ContractQueryService;
import dev.dimi.erp.contratos.internal.web.dto.AdjustmentApiResponse;
import dev.dimi.erp.contratos.internal.web.dto.CreateAdjustmentRequest;
import dev.dimi.erp.contratos.internal.web.dto.CreateResponse;
import dev.dimi.erp.identidade.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code contract_adjustment} (reajustes por índice). Separado de {@link
 * ContractController} pelo mesmo motivo de {@link AmendmentController}: cada controller foca em uma
 * responsabilidade do contrato.
 *
 * <p>Cada POST cria um {@link AdjustmentId} novo (append-only — UNIQUE {@code (contract_id,
 * applied_on)} no banco impede dois reajustes na mesma data). Range {@code [-50, +500]} é validado
 * no service ({@code AdjustContractService}), não aqui — typo de operador deve subir como {@code
 * InvalidAdjustmentPercentageException} com mensagem clara.
 */
@RestController
@RequestMapping("/api/v1/contratos")
@RequiredArgsConstructor
class AdjustmentController {

  private final AdjustContractService adjustService;
  private final ContractQueryService queryService;

  @PostMapping("/{id}/adjustments")
  @PreAuthorize("hasAuthority('contrato.reajustar')")
  public ResponseEntity<CreateResponse> create(
      @PathVariable("id") UUID id,
      @Valid @RequestBody CreateAdjustmentRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    AdjustmentId adjustmentId =
        adjustService.apply(
            new ContractId(id),
            body.index(),
            body.percentage(),
            body.appliedOn(),
            body.notes(),
            principal.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(CreateResponse.of(adjustmentId));
  }

  @GetMapping("/{id}/adjustments")
  @PreAuthorize("hasAuthority('contrato.consultar')")
  public List<AdjustmentApiResponse> list(@PathVariable("id") UUID id) {
    return queryService.findAdjustments(new ContractId(id)).stream()
        .map(AdjustmentApiResponse::from)
        .toList();
  }
}
