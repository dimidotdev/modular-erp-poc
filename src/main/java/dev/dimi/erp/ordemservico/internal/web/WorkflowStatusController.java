package dev.dimi.erp.ordemservico.internal.web;

import dev.dimi.erp.ordemservico.WorkflowStatusId;
import dev.dimi.erp.ordemservico.internal.application.WorkflowQueryService;
import dev.dimi.erp.ordemservico.internal.web.dto.WorkflowStatusApiResponse;
import dev.dimi.erp.ordemservico.internal.web.dto.WorkflowTransitionApiResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only do catálogo de workflow ({@code /api/v1/ordens-servico/workflow/**}). CRUD de status
 * custom e transições entra em 9.2.
 */
@RestController
@RequestMapping("/api/v1/ordens-servico/workflow")
@RequiredArgsConstructor
class WorkflowStatusController {

  private final WorkflowQueryService workflowQueryService;

  @GetMapping("/statuses")
  @PreAuthorize("hasAuthority('os.consultar')")
  public List<WorkflowStatusApiResponse> listStatuses() {
    return workflowQueryService.listStatuses().stream()
        .map(WorkflowStatusApiResponse::from)
        .toList();
  }

  @GetMapping("/transitions")
  @PreAuthorize("hasAuthority('os.consultar')")
  public List<WorkflowTransitionApiResponse> transitionsFrom(
      @RequestParam(name = "from") UUID fromStatusId) {
    return workflowQueryService.transitionsFrom(new WorkflowStatusId(fromStatusId)).stream()
        .map(WorkflowTransitionApiResponse::from)
        .toList();
  }
}
