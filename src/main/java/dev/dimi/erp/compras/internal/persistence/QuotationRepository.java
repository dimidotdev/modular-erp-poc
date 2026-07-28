package dev.dimi.erp.compras.internal.persistence;

import dev.dimi.erp.compras.QuotationStatus;
import dev.dimi.erp.compras.internal.domain.Quotation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface QuotationRepository extends JpaRepository<Quotation, UUID> {

  Optional<Quotation> findByBranchIdAndNumber(UUID branchId, long number);

  Page<Quotation> findByBranchIdAndStatus(UUID branchId, QuotationStatus status, Pageable pageable);

  List<Quotation> findByRequestId(UUID requestId);
}
