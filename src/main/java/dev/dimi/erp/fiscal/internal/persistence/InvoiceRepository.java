package dev.dimi.erp.fiscal.internal.persistence;

import dev.dimi.erp.fiscal.internal.domain.Invoice;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {
  java.util.Optional<Invoice> findByBranchIdAndModelAndSeriesAndNumber(
      java.util.UUID branchId, dev.dimi.erp.fiscal.FiscalModel model, String series, long number);

  java.util.Optional<Invoice> findByIdempotencyKey(String idempotencyKey);

  java.util.Optional<Invoice> findByOriginTypeAndOriginIdAndModel(
      dev.dimi.erp.fiscal.FiscalOriginType originType,
      java.util.UUID originId,
      dev.dimi.erp.fiscal.FiscalModel model);

  java.util.Optional<Invoice> findByFiscalKey(String fiscalKey);
}
