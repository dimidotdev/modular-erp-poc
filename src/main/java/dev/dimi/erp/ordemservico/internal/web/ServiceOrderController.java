package dev.dimi.erp.ordemservico.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.WorkflowStatusId;
import dev.dimi.erp.ordemservico.internal.application.ChangeStatusService;
import dev.dimi.erp.ordemservico.internal.application.OpenOsCommand;
import dev.dimi.erp.ordemservico.internal.application.OpenOsService;
import dev.dimi.erp.ordemservico.internal.application.ServiceOrderQueryService;
import dev.dimi.erp.ordemservico.internal.web.dto.CancelOsRequest;
import dev.dimi.erp.ordemservico.internal.web.dto.OpenOsRequest;
import dev.dimi.erp.ordemservico.internal.web.dto.OpenOsResponse;
import dev.dimi.erp.ordemservico.internal.web.dto.ServiceOrderApiResponse;
import dev.dimi.erp.ordemservico.internal.web.dto.StatusHistoryApiResponse;
import dev.dimi.erp.ordemservico.internal.web.dto.TransitionRequest;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code service_order} ({@code /api/v1/ordens-servico/**}).
 *
 * <p>Slice 9.1: CRUD básico + transições de status validadas pelo grafo. Itens, billing e Saga de
 * cancelamento entram em 9.2.
 */
@RestController
@RequestMapping("/api/v1/ordens-servico")
@RequiredArgsConstructor
class ServiceOrderController {

  private static final int DEFAULT_PAGE_SIZE = 20;
  private static final int MAX_PAGE_SIZE = 100;

  private final OpenOsService openOsService;
  private final ChangeStatusService changeStatusService;
  private final ServiceOrderQueryService queryService;

  /** Lista paginada — filtra por branch obrigatório; opcional por status ou por customer. */
  @GetMapping
  @PreAuthorize("hasAuthority('os.consultar')")
  public Page<ServiceOrderApiResponse> list(
      @RequestParam(name = "branchId") UUID branchId,
      @RequestParam(name = "statusId", required = false) UUID statusId,
      @RequestParam(name = "customerId", required = false) UUID customerId,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable =
        PageRequest.of(
            Math.max(0, page), clampPageSize(size), Sort.by(Sort.Direction.DESC, "openedAt"));
    Page<?> raw;
    if (customerId != null) {
      raw = queryService.listByCustomer(new PersonId(customerId), pageable);
    } else if (statusId != null) {
      raw =
          queryService.listByBranchAndStatus(
              new BranchId(branchId), new WorkflowStatusId(statusId), pageable);
    } else {
      raw = queryService.listByBranch(new BranchId(branchId), pageable);
    }
    @SuppressWarnings("unchecked")
    Page<dev.dimi.erp.ordemservico.internal.domain.ServiceOrder> typed =
        (Page<dev.dimi.erp.ordemservico.internal.domain.ServiceOrder>) raw;
    return typed.map(ServiceOrderApiResponse::from);
  }

  @PostMapping
  @PreAuthorize("hasAuthority('os.criar')")
  public ResponseEntity<OpenOsResponse> open(
      @Valid @RequestBody OpenOsRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    ServiceOrderId id =
        openOsService.open(
            new OpenOsCommand(
                new BranchId(body.branchId()),
                new PersonId(body.customerId()),
                body.contactId(),
                body.priority(),
                body.assignedTo(),
                body.paymentConditionId(),
                body.equipmentDescription(),
                body.serialNumber(),
                body.problemReported(),
                body.notes()),
            principal.userId());
    return ResponseEntity.status(HttpStatus.CREATED).body(OpenOsResponse.of(id));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('os.consultar')")
  public ServiceOrderApiResponse get(@PathVariable("id") UUID id) {
    return ServiceOrderApiResponse.from(queryService.findById(new ServiceOrderId(id)));
  }

  @GetMapping("/{id}/history")
  @PreAuthorize("hasAuthority('os.consultar')")
  public List<StatusHistoryApiResponse> history(@PathVariable("id") UUID id) {
    return queryService.historyOf(new ServiceOrderId(id)).stream()
        .map(StatusHistoryApiResponse::from)
        .toList();
  }

  /**
   * Transição genérica. Permissão {@code os.editar} cobre o caso comum; permissões específicas por
   * transição ({@code required_permission} no grafo) entram em 9.2 com gating dinâmico.
   */
  @PostMapping("/{id}/transitions")
  @PreAuthorize("hasAuthority('os.editar')")
  @ResponseStatusNoContent
  public void transition(
      @PathVariable("id") UUID id,
      @Valid @RequestBody TransitionRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    changeStatusService.transitionTo(
        new ServiceOrderId(id), body.targetCode(), body.notes(), principal.userId());
  }

  @PostMapping("/{id}/cancel")
  @PreAuthorize("hasAuthority('os.cancelar')")
  @ResponseStatusNoContent
  public void cancel(
      @PathVariable("id") UUID id,
      @Valid @RequestBody CancelOsRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    changeStatusService.cancel(new ServiceOrderId(id), body.reason(), principal.userId());
  }

  @PostMapping("/{id}/close")
  @PreAuthorize("hasAuthority('os.fechar')")
  @ResponseStatusNoContent
  public void close(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    changeStatusService.close(new ServiceOrderId(id), principal.userId());
  }

  private int clampPageSize(int size) {
    if (size <= 0) return DEFAULT_PAGE_SIZE;
    return Math.min(size, MAX_PAGE_SIZE);
  }

  // alias meta-annotation pra evitar duplicar @ResponseStatus(HttpStatus.NO_CONTENT)
  @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
  @java.lang.annotation.Target(java.lang.annotation.ElementType.METHOD)
  @org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.NO_CONTENT)
  private @interface ResponseStatusNoContent {}
}
