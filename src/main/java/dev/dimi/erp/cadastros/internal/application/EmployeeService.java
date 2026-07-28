package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.EmployeeHiredEvent;
import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.cadastros.EmployeeTerminatedEvent;
import dev.dimi.erp.cadastros.EmployeeUpdatedEvent;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.domain.Employee;
import dev.dimi.erp.cadastros.internal.persistence.BranchRepository;
import dev.dimi.erp.cadastros.internal.persistence.EmployeeRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sobre vínculos {@code Employee} dentro do tenant atual.
 *
 * <p>Cobre o slice 3.3: contratar, atualizar (parcial), desligar, listar/buscar. RN-CAD-010
 * garantido por checagem aplicacional + índice parcial {@code uq_employee_active}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EmployeeService {

  private final EmployeeRepository employees;
  private final PersonRepository persons;
  private final BranchRepository branches;
  private final ApplicationEventPublisher events;

  // ---------------------------------------------------------------------------
  // Contratação
  // ---------------------------------------------------------------------------

  @Transactional
  public EmployeeSummary hire(HireEmployeeCommand cmd) {
    var person =
        persons
            .findById(cmd.personId().value())
            .orElseThrow(
                () ->
                    new InvalidEmployeeStateException(
                        new EmployeeId(java.util.UUID.randomUUID()),
                        "person-not-found",
                        "Pessoa informada não existe no tenant atual."));
    if (!person.isActive() || person.isAnonymized()) {
      throw new InvalidEmployeeStateException(
          new EmployeeId(java.util.UUID.randomUUID()),
          "person-inactive",
          "Pessoa inativa ou anonimizada não pode ser contratada.");
    }

    var branch =
        branches
            .findById(cmd.branchId().value())
            .orElseThrow(
                () ->
                    new InvalidEmployeeStateException(
                        new EmployeeId(java.util.UUID.randomUUID()),
                        "branch-not-found",
                        "Filial informada não existe no tenant atual."));
    if (!branch.isActive()) {
      throw new InvalidEmployeeStateException(
          new EmployeeId(java.util.UUID.randomUUID()),
          "branch-inactive",
          "Filial inativa não pode receber novas contratações.");
    }

    if (employees
        .findActiveByPersonAndBranch(cmd.personId().value(), cmd.branchId().value())
        .isPresent()) {
      throw new EmployeeAlreadyHiredException(cmd.personId(), cmd.branchId());
    }

    Employee saved =
        employees.save(
            Employee.hire(
                cmd.personId().value(),
                cmd.branchId().value(),
                cmd.jobTitleId(),
                cmd.departmentId(),
                cmd.salary(),
                cmd.hiredAt(),
                cmd.notes()));

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new EmployeeHiredEvent(
            saved.id(),
            cmd.personId(),
            cmd.branchId(),
            new TenantId(tenant.id()),
            saved.getHiredAt(),
            cmd.hiredBy(),
            Instant.now()));

    log.info(
        "funcionario contratado id={} person={} branch={} hiredAt={}",
        saved.id().value(),
        cmd.personId().value(),
        cmd.branchId().value(),
        saved.getHiredAt());
    return EmployeeSummary.of(saved);
  }

  // ---------------------------------------------------------------------------
  // Atualização parcial
  // ---------------------------------------------------------------------------

  @Transactional
  public EmployeeSummary update(UpdateEmployeeCommand cmd) {
    Employee e =
        employees.findById(cmd.employeeId().value()).orElseThrow(() -> notFound(cmd.employeeId()));
    if (e.getTerminatedAt() != null) {
      throw new InvalidEmployeeStateException(
          e.id(), "terminated", "Funcionário desligado não pode ser editado.");
    }

    Set<String> changed = new HashSet<>();
    if (cmd.jobTitleId() != null && !cmd.jobTitleId().equals(e.getJobTitleId())) {
      changed.add("jobTitleId");
    }
    if (cmd.departmentId() != null && !cmd.departmentId().equals(e.getDepartmentId())) {
      changed.add("departmentId");
    }
    if (cmd.salary() != null && !cmd.salary().equals(e.getSalary())) {
      changed.add("salary");
    }
    if (cmd.notes() != null && !cmd.notes().equals(e.getNotes())) {
      changed.add("notes");
    }

    e.update(cmd.jobTitleId(), cmd.departmentId(), cmd.salary(), cmd.notes());

    if (!changed.isEmpty()) {
      Tenant tenant = TenantContext.currentOrThrow();
      events.publishEvent(
          new EmployeeUpdatedEvent(
              e.id(), new TenantId(tenant.id()), changed, cmd.updatedBy(), Instant.now()));
      log.info("funcionario atualizado id={} fields={}", e.id().value(), changed);
    }
    return EmployeeSummary.of(e);
  }

  // ---------------------------------------------------------------------------
  // Desligamento
  // ---------------------------------------------------------------------------

  @Transactional
  public EmployeeSummary terminate(
      EmployeeId employeeId, LocalDate terminatedAt, String reason, UserId actor) {
    Employee e = employees.findById(employeeId.value()).orElseThrow(() -> notFound(employeeId));
    if (e.getTerminatedAt() != null) {
      throw new InvalidEmployeeStateException(
          e.id(), "already-terminated", "Funcionário já foi desligado.");
    }
    if (terminatedAt.isBefore(e.getHiredAt())) {
      throw new InvalidEmployeeStateException(
          e.id(),
          "terminate-before-hire",
          "Data de desligamento não pode ser anterior à admissão.");
    }
    e.terminate(terminatedAt);

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new EmployeeTerminatedEvent(
            e.id(),
            new PersonId(e.getPersonId()),
            new BranchId(e.getBranchId()),
            new TenantId(tenant.id()),
            terminatedAt,
            reason,
            actor,
            Instant.now()));

    log.info(
        "funcionario desligado id={} terminatedAt={} reason={}",
        e.id().value(),
        terminatedAt,
        reason);
    return EmployeeSummary.of(e);
  }

  // ---------------------------------------------------------------------------
  // Queries
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public EmployeeSummary findById(EmployeeId employeeId) {
    Employee e = employees.findById(employeeId.value()).orElseThrow(() -> notFound(employeeId));
    return EmployeeSummary.of(e);
  }

  @Transactional(readOnly = true)
  public Page<EmployeeSummary> list(Pageable pageable) {
    return employees.findAll(pageable).map(EmployeeSummary::of);
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static EmployeeNotFoundException notFound(EmployeeId id) {
    return new EmployeeNotFoundException(id);
  }
}
