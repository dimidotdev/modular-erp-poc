package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.FinancialApportionment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialApportionmentRepository
    extends JpaRepository<FinancialApportionment, UUID> {

  java.util.List<FinancialApportionment> findByRecordId(UUID recordId);
}
