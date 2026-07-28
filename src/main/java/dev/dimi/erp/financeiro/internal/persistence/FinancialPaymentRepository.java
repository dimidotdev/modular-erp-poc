package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.FinancialPayment;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialPaymentRepository extends JpaRepository<FinancialPayment, UUID> {

  Optional<FinancialPayment> findByIdempotencyKey(String idempotencyKey);

  java.util.List<FinancialPayment> findByBatchId(UUID batchId);
}
