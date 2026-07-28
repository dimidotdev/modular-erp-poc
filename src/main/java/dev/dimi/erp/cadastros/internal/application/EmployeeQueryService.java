package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.cadastros.EmployeeQueryPort;
import dev.dimi.erp.cadastros.EmployeeView;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.domain.Employee;
import dev.dimi.erp.cadastros.internal.persistence.EmployeeRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Adapter interno que expõe a porta {@link EmployeeQueryPort}. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class EmployeeQueryService implements EmployeeQueryPort {

  private final EmployeeRepository employees;

  @Override
  public Optional<EmployeeView> findById(EmployeeId id) {
    return employees.findById(id.value()).map(EmployeeQueryService::toView);
  }

  @Override
  public boolean exists(EmployeeId id) {
    return employees.existsById(id.value());
  }

  @Override
  public Optional<EmployeeView> findActive(PersonId personId, BranchId branchId) {
    return employees
        .findActiveByPersonAndBranch(personId.value(), branchId.value())
        .map(EmployeeQueryService::toView);
  }

  @Override
  public List<EmployeeView> listActiveByBranch(BranchId branchId) {
    return employees.findByBranchIdAndActiveTrue(branchId.value()).stream()
        .map(EmployeeQueryService::toView)
        .toList();
  }

  private static EmployeeView toView(Employee e) {
    return new EmployeeView(
        e.id(),
        new PersonId(e.getPersonId()),
        new BranchId(e.getBranchId()),
        e.getHiredAt(),
        e.getTerminatedAt(),
        e.isActive());
  }
}
