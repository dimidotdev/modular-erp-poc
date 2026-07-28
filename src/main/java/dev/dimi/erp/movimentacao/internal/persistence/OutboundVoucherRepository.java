package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.OutboundReason;
import dev.dimi.erp.movimentacao.internal.domain.OutboundVoucher;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundVoucherRepository extends JpaRepository<OutboundVoucher, UUID> {

  Optional<OutboundVoucher> findByBranchIdAndNumber(UUID branchId, long number);

  Page<OutboundVoucher> findByBranchIdAndReason(
      UUID branchId, OutboundReason reason, Pageable pageable);

  Page<OutboundVoucher> findByBranchId(UUID branchId, Pageable pageable);
}
