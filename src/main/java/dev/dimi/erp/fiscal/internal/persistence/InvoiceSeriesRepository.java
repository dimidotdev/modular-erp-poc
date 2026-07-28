package dev.dimi.erp.fiscal.internal.persistence;

import dev.dimi.erp.fiscal.FiscalModel;
import dev.dimi.erp.fiscal.internal.domain.InvoiceSeries;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InvoiceSeriesRepository extends JpaRepository<InvoiceSeries, UUID> {

  Optional<InvoiceSeries> findByBranchIdAndModelAndSeries(
      UUID branchId, FiscalModel model, String series);

  /**
   * RN-FIS-002: lock pessimista no row de {@code invoice_series} antes de incrementar {@code
   * next_number}. Garante unicidade do número emitido sob concorrência sem depender de retry
   * otimista (que ressuscitaria o gap).
   */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select s from InvoiceSeries s "
          + "where s.branchId = :branchId "
          + "  and s.model = :model "
          + "  and s.series = :series")
  Optional<InvoiceSeries> findForAllocation(
      @Param("branchId") UUID branchId,
      @Param("model") FiscalModel model,
      @Param("series") String series);
}
