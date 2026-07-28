package dev.dimi.erp.fiscal.internal.persistence;

import dev.dimi.erp.fiscal.internal.domain.TaxCalculation;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TaxCalculationRepository extends JpaRepository<TaxCalculation, UUID> {
  java.util.List<TaxCalculation> findByInvoiceItemId(java.util.UUID invoiceItemId);
}
