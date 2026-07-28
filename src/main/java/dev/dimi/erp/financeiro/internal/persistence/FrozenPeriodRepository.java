package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.FrozenPeriod;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FrozenPeriodRepository extends JpaRepository<FrozenPeriod, UUID> {

  java.util.Optional<FrozenPeriod> findByBranchIdAndPeriodStartAndPeriodEnd(
      UUID branchId, java.time.LocalDate periodStart, java.time.LocalDate periodEnd);
}
