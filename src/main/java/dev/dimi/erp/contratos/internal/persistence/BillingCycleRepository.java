package dev.dimi.erp.contratos.internal.persistence;

import dev.dimi.erp.contratos.internal.domain.BillingCycle;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BillingCycleRepository extends JpaRepository<BillingCycle, UUID> {

  Optional<BillingCycle> findByContractIdAndCompetenceMonth(
      UUID contractId, LocalDate competenceMonth);

  List<BillingCycle> findByContractIdOrderByCompetenceMonthDesc(UUID contractId);

  @Query(
      "select c from BillingCycle c where c.status = 'PENDING' and c.competenceMonth <= :until "
          + "order by c.competenceMonth asc")
  List<BillingCycle> findPendingUpTo(@Param("until") LocalDate until);

  @Query("select c from BillingCycle c where c.status = 'FAILED' order by c.competenceMonth asc")
  List<BillingCycle> findAllFailed();
}
