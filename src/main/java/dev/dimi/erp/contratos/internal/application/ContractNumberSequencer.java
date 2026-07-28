package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.internal.persistence.ContractRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sequencial por filial para {@code contract.number}. Implementação via {@code max(number)+1}
 * dentro da transação corrente; UNIQUE constraint {@code (branch_id, number)} protege contra race —
 * caller deve capturar {@code DataIntegrityViolationException} e recalcular (padrão "optimistic
 * numbering", mesmo {@code OsNumberSequencer}).
 */
@Component
class ContractNumberSequencer {

  private final ContractRepository repo;

  ContractNumberSequencer(ContractRepository repo) {
    this.repo = repo;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  long nextFor(UUID branchId) {
    return repo.maxNumberByBranch(branchId) + 1L;
  }
}
