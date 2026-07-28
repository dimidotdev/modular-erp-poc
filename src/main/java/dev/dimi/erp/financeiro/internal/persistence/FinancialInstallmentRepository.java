package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.FinancialInstallment;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialInstallmentRepository extends JpaRepository<FinancialInstallment, UUID> {

  java.util.List<FinancialInstallment> findByRecordIdOrderBySequenceAsc(UUID recordId);
}
