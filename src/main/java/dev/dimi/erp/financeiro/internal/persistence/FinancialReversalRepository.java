package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.FinancialReversal;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialReversalRepository extends JpaRepository<FinancialReversal, UUID> {

  Optional<FinancialReversal> findByOriginalPaymentId(UUID originalPaymentId);
}
