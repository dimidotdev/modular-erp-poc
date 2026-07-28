package dev.dimi.erp.contratos.internal.web;

import dev.dimi.erp.contratos.AmendmentId;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.internal.application.AmendContractService;
import dev.dimi.erp.contratos.internal.application.ContractQueryService;
import dev.dimi.erp.contratos.internal.web.dto.AmendmentApiResponse;
import dev.dimi.erp.contratos.internal.web.dto.CreateAmendmentRequest;
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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code contract_amendment} ({@code /api/v1/contratos/.../amendments}).
 *
 * <p>Separado de {@link ContractController} para focar em responsabilidades distintas — o
 * controller principal lida com lifecycle do contrato, este com aditivos. Mesmo padrão de {@code
 * OsBillingController} vs {@code ServiceOrderController} no slice 9.x.
 *
 * <p>Step-up auth para efetivação fica adiada — em v1 basta {@code contrato.aditivar} no token.
 * Reforço de step-up (impacto em valor/prazo) pode ser ligado em 10.x ou 11 com {@code
 * AuthenticatedUser.hasFreshStepUp(...)}.
 */
@RestController
@RequestMapping("/api/v1/contratos")
@RequiredArgsConstructor
class AmendmentController {

  private final AmendContractService amendService;
  private final ContractQueryService queryService;

  @PostMapping("/{id}/amendments")
  @PreAuthorize("hasAuthority('contrato.aditivar')")
  public ResponseEntity<CreateResponse> create(
      @PathVariable("id") UUID id,
      @Valid @RequestBody CreateAmendmentRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    AmendmentId amendmentId =
        amendService.createAmendment(
            new ContractId(id),
            body.type(),
            body.effectiveFrom(),
            body.description(),
            body.newEndDate(),
            body.newBaseValue(),
            body.payload(),
            principal.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(CreateResponse.of(amendmentId));
  }

  @PostMapping("/amendments/{amendmentId}/effective")
  @PreAuthorize("hasAuthority('contrato.aditivar')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void effectivate(
      @PathVariable("amendmentId") UUID amendmentId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    amendService.effectivate(new AmendmentId(amendmentId), principal.userId());
  }

  @GetMapping("/{id}/amendments")
  @PreAuthorize("hasAuthority('contrato.consultar')")
  public List<AmendmentApiResponse> list(@PathVariable("id") UUID id) {
    return queryService.findAmendments(new ContractId(id)).stream()
        .map(AmendmentApiResponse::from)
        .toList();
  }
}
