package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.internal.domain.QuotationResponse;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationResponseRepository extends JpaRepository<QuotationResponse, UUID> {

  List<QuotationResponse> findByQuotationId(UUID quotationId);

  List<QuotationResponse> findByQuotationIdAndSupplierId(UUID quotationId, UUID supplierId);
}
