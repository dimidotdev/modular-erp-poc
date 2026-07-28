package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.TransferStatus;
import dev.dimi.erp.movimentacao.internal.domain.StockTransfer;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockTransferRepository extends JpaRepository<StockTransfer, UUID> {

  Optional<StockTransfer> findByOriginBranchIdAndNumber(UUID originBranchId, long number);

  Page<StockTransfer> findByOriginBranchIdAndStatus(
      UUID originBranchId, TransferStatus status, Pageable pageable);
}
