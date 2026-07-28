package dev.dimi.erp.ordemservico.internal.persistence;

import dev.dimi.erp.ordemservico.internal.domain.Billing;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingRepository extends JpaRepository<Billing, UUID> {

  Optional<Billing> findByIdempotencyKey(String key);

  List<Billing> findByOsIdOrderByIssuedAtAsc(UUID osId);
}
