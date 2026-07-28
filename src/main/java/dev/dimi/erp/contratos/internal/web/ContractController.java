package dev.dimi.erp.contratos.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractStatus;
import dev.dimi.erp.contratos.internal.application.ActivateContractService;
import dev.dimi.erp.contratos.internal.application.ContractQueryService;
import dev.dimi.erp.contratos.internal.application.CreateContractCommand;
import dev.dimi.erp.contratos.internal.application.CreateContractService;
import dev.dimi.erp.contratos.internal.application.ResumeContractService;
import dev.dimi.erp.contratos.internal.application.SuspendContractService;
import dev.dimi.erp.contratos.internal.application.TerminateContractService;
import dev.dimi.erp.contratos.internal.web.dto.ContractApiResponse;
import dev.dimi.erp.contratos.internal.web.dto.ContractItemApiResponse;
import dev.dimi.erp.contratos.internal.web.dto.CreateContractRequest;
import dev.dimi.erp.contratos.internal.web.dto.CreateResponse;
import dev.dimi.erp.contratos.internal.web.dto.SuspendRequest;
import dev.dimi.erp.contratos.internal.web.dto.TerminateRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * Endpoints REST de {@code contract} ({@code /api/v1/contratos/**}).
 *
 * <p>Slice 10.1: CRUD + lifecycle (activate/suspend/resume/terminate) + listagem de itens. Aditivos
 * e reajustes ficam em controllers próprios ({@link AmendmentController}/{@link
 * AdjustmentController}) para manter cada controller focado em sua responsabilidade — mesmo padrão
 * de {@code ServiceOrderController} vs {@code OsBillingController} no slice 9.x.
 *
 * <p>Geração de billing cycles e endpoint manual de faturamento ({@code contrato.faturar}) entram
 * em 10.2 junto com o job diário e o listener financeiro.
 */
@RestController
@RequestMapping("/api/v1/contratos")
@RequiredArgsConstructor
class ContractController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final CreateContractService createService;
  private final ActivateContractService activateService;
  private final SuspendContractService suspendService;
  private final ResumeContractService resumeService;
  private final TerminateContractService terminateService;
  private final ContractQueryService queryService;

  /** Lista paginada — filtra por branch obrigatório; opcional por status. */
  @GetMapping
  @PreAuthorize("hasAuthority('contrato.consultar')")
  public Page<ContractApiResponse> list(
      @RequestParam(name = "branchId") UUID branchId,
      @RequestParam(name = "status", required = false) ContractStatus status,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(0, page), clampPageSize(size), Sort.by(Sort.Direction.DESC, "createdAt"));
    Page<dev.dimi.erp.contratos.internal.domain.Contract> raw =
        status != null
            ? queryService.listByBranchAndStatus(new BranchId(branchId), status, pageable)
            : queryService.listByBranch(new BranchId(branchId), pageable);
    return raw.map(ContractApiResponse::from);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('contrato.criar')")
  public ResponseEntity<CreateResponse> create(
      @Valid @RequestBody CreateContractRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    ContractId id =
        createService.create(
            new CreateContractCommand(
                new BranchId(body.branchId()),
                new PersonId(body.customerId()),
                body.contactId(),
                body.title(),
                body.description(),
                body.startDate(),
                body.endDate(),
                body.recurrence(),
                body.billingDay(),
                body.baseValue(),
                body.paymentConditionId(),
                body.costCenterId(),
                body.autoRenew(),
                body.renewalPeriodMonths(),
                body.cancellationClause(),
                body.cancellationFeeValue(),
                body.cancellationNoticeDays(),
                body.notes(),
                body.items().stream()
                    .map(CreateContractRequest.ContractItemRequest::toSpec)
                    .toList()),
            principal.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(CreateResponse.of(id));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('contrato.consultar')")
  public ContractApiResponse get(@PathVariable("id") UUID id) {
    return ContractApiResponse.from(queryService.findById(new ContractId(id)));
  }

  @GetMapping("/{id}/items")
  @PreAuthorize("hasAuthority('contrato.consultar')")
  public List<ContractItemApiResponse> items(@PathVariable("id") UUID id) {
    return queryService.findItems(new ContractId(id)).stream()
        .map(ContractItemApiResponse::from)
        .toList();
  }

  @PostMapping("/{id}/activate")
  @PreAuthorize("hasAuthority('contrato.ativar')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void activate(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    activateService.activate(new ContractId(id), principal.userId());
  }

  @PostMapping("/{id}/suspend")
  @PreAuthorize("hasAuthority('contrato.suspender')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void suspend(
      @PathVariable("id") UUID id,
      @Valid @RequestBody SuspendRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    suspendService.suspend(new ContractId(id), body.reason(), principal.userId());
  }

  @PostMapping("/{id}/resume")
  @PreAuthorize("hasAuthority('contrato.suspender')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void resume(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    resumeService.resume(new ContractId(id), principal.userId());
  }

  @PostMapping("/{id}/terminate")
  @PreAuthorize("hasAuthority('contrato.encerrar')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void terminate(
      @PathVariable("id") UUID id,
      @Valid @RequestBody TerminateRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    terminateService.terminate(
        new ContractId(id),
        body.terminationDate(),
        body.reason(),
        body.earlyTermination(),
        principal.userId());
  }

  private int clampPageSize(int size) {
    if (size <= 0) return DEFAULT_PAGE_SIZE;
    return Math.min(size, MAX_PAGE_SIZE);
  }
}
