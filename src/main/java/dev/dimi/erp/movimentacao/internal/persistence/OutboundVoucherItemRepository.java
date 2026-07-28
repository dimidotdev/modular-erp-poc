package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.internal.domain.OutboundVoucherItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboundVoucherItemRepository extends JpaRepository<OutboundVoucherItem, UUID> {

  List<OutboundVoucherItem> findByVoucherIdOrderByLineNumberAsc(UUID voucherId);

  void deleteByVoucherId(UUID voucherId);
}
