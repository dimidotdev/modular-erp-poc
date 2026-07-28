package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.FinancialRecord;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialRecordRepository extends JpaRepository<FinancialRecord, UUID> {

  Optional<FinancialRecord> findByBranchIdAndNumber(UUID branchId, long number);

  Optional<FinancialRecord> findByIdempotencyKey(String idempotencyKey);

  Optional<FinancialRecord> findByOriginTypeAndOriginId(
      dev.dimi.erp.financeiro.FinancialOriginType originType, UUID originId);
}
