package dev.dimi.erp.contratos.internal.persistence;

import dev.dimi.erp.contratos.internal.domain.Adjustment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdjustmentRepository extends JpaRepository<Adjustment, UUID> {

  List<Adjustment> findByContractIdOrderByAppliedOnDesc(UUID contractId);
}
