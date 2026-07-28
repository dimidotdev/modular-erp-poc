package dev.dimi.erp.contratos.internal.persistence;

import dev.dimi.erp.contratos.internal.domain.ContractItem;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ContractItemRepository extends JpaRepository<ContractItem, UUID> {

  List<ContractItem> findByContractIdOrderByLineNumberAsc(UUID contractId);

  @Query(
      "select coalesce(max(i.lineNumber), 0) from ContractItem i where i.contractId = :contractId")
  int maxLineByContract(UUID contractId);
}
