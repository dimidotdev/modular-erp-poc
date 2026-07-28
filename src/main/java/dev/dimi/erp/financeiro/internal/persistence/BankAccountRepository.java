package dev.dimi.erp.financeiro.internal.persistence;

import dev.dimi.erp.financeiro.internal.domain.BankAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {

  Optional<BankAccount> findByCode(String code);
}
