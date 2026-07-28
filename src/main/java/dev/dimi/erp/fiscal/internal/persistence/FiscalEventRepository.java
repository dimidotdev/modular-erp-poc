package dev.dimi.erp.fiscal.internal.persistence;

import dev.dimi.erp.fiscal.internal.domain.FiscalEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FiscalEventRepository extends JpaRepository<FiscalEvent, UUID> {
  java.util.List<FiscalEvent> findByInvoiceIdOrderByOccurredAtAsc(java.util.UUID invoiceId);
}
