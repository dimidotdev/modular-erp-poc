package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.ReturnStatus;
import dev.dimi.erp.movimentacao.internal.domain.SaleReturn;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SaleReturnRepository extends JpaRepository<SaleReturn, UUID> {

  List<SaleReturn> findBySaleId(UUID saleId);

  Page<SaleReturn> findByBranchIdAndStatus(UUID branchId, ReturnStatus status, Pageable pageable);
}
