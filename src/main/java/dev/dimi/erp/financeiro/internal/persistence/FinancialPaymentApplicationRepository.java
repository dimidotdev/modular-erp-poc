package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.FinancialPaymentApplication;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FinancialPaymentApplicationRepository
    extends JpaRepository<FinancialPaymentApplication, UUID> {

  java.util.List<FinancialPaymentApplication> findByPaymentId(UUID paymentId);
}
