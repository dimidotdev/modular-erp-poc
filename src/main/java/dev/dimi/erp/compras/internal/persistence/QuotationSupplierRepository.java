package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.internal.domain.QuotationSupplier;
import dev.dimi.erp.compras.internal.domain.QuotationSupplierKey;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationSupplierRepository
    extends JpaRepository<QuotationSupplier, QuotationSupplierKey> {

  List<QuotationSupplier> findByKeyQuotationId(UUID quotationId);

  List<QuotationSupplier> findByKeySupplierId(UUID supplierId);
}
