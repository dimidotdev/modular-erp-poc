package dev.dimi.erp.contratos.internal.persistence;

import dev.dimi.erp.contratos.ContractStatus;
import dev.dimi.erp.contratos.internal.domain.Contract;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContractRepository extends JpaRepository<Contract, UUID> {

  Optional<Contract> findByBranchIdAndNumber(UUID branchId, long number);

  Page<Contract> findByBranchId(UUID branchId, Pageable pageable);

  Page<Contract> findByStatusAndBranchId(ContractStatus status, UUID branchId, Pageable pageable);

  @Query("select coalesce(max(c.number), 0) from Contract c where c.branchId = :branchId")
  long maxNumberByBranch(UUID branchId);

  /**
   * Lista todos os contratos em estado {@code ACTIVE} no tenant atual. Usado pelo {@code
   * ScheduleBillingCyclesService} (slice 10.2) para materializar ciclos futuros — varredura diária
   * curta dentro de um schema; sem paginação por tratar-se de job batch que precisa do conjunto
   * completo. Para tenants grandes, refinement futuro pode paginar via cursor sobre {@code id}.
   */
  @Query("select c from Contract c where c.status = dev.dimi.erp.contratos.ContractStatus.ACTIVE")
  List<Contract> findByStatusActive();
}
