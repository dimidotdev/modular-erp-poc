package dev.dimi.erp.fiscal.internal.persistence;

import dev.dimi.erp.fiscal.internal.domain.InvoiceItem;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InvoiceItemRepository extends JpaRepository<InvoiceItem, UUID> {
  java.util.List<InvoiceItem> findByInvoiceIdOrderByLineNumberAsc(java.util.UUID invoiceId);
}
