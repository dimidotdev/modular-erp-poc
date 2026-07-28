package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.ChartAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChartAccountRepository extends JpaRepository<ChartAccount, UUID> {

  Optional<ChartAccount> findByCode(String code);
}
