package dev.dimi.erp.contratos.internal.persistence;

import dev.dimi.erp.contratos.internal.domain.Amendment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AmendmentRepository extends JpaRepository<Amendment, UUID> {

  List<Amendment> findByContractIdOrderByCreatedAtDesc(UUID contractId);

  Optional<Amendment> findByIdAndContractId(UUID id, UUID contractId);
}
