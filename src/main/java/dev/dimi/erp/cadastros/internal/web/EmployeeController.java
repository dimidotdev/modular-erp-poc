package dev.dimi.erp.cadastros.internal.web;

import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.cadastros.internal.application.EmployeeService;
import dev.dimi.erp.cadastros.internal.web.dto.EmployeeApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.EmployeePageApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.HireEmployeeApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.TerminateEmployeeApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.UpdateEmployeeApiRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code Employee} ({@code /api/v1/cadastros/employees/**}).
 *
 * <p>Permissions:
 *
 * <ul>
 *   <li>{@code funcionario.consultar} — listar, buscar por id.
 *   <li>{@code funcionario.contratar} — contratar nova pessoa em filial.
 *   <li>{@code funcionario.editar} — atualizar cargo/departamento/salário/notas.
 *   <li>{@code funcionario.desligar} + step-up — desligar funcionário (RN-CAD-010 libera vínculo).
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/cadastros/employees")
@RequiredArgsConstructor
class EmployeeController {

  private static final int MAX_PAGE_SIZE = 100;

  private final EmployeeService employeeService;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('funcionario.contratar')")
  EmployeeApiResponse hire(
      @RequestBody @Valid HireEmployeeApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return EmployeeApiResponse.from(employeeService.hire(body.toCommand(principal.userId())));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('funcionario.consultar')")
  EmployeePageApiResponse list(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    return EmployeePageApiResponse.from(employeeService.list(pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('funcionario.consultar')")
  EmployeeApiResponse findById(@PathVariable("id") UUID id) {
    return EmployeeApiResponse.from(employeeService.findById(new EmployeeId(id)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('funcionario.editar')")
  EmployeeApiResponse update(
      @PathVariable("id") UUID id,
      @RequestBody @Valid UpdateEmployeeApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return EmployeeApiResponse.from(
        employeeService.update(body.toCommand(new EmployeeId(id), principal.userId())));
  }

  @PostMapping("/{id}/terminate")
  @PreAuthorize("hasAuthority('funcionario.desligar')")
  EmployeeApiResponse terminate(
      @PathVariable("id") UUID id,
      @RequestBody @Valid TerminateEmployeeApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return EmployeeApiResponse.from(
        employeeService.terminate(
            new EmployeeId(id), body.terminatedAt(), body.reason(), principal.userId()));
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
