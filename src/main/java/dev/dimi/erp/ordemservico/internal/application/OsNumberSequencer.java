package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.ordemservico.internal.persistence.ServiceOrderRepository;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sequencial por filial. Implementação via {@code max(number)+1} dentro da transação corrente;
 * UNIQUE constraint {@code (branch_id, number)} protege contra race — caller deve capturar {@code
 * DataIntegrityViolationException} e recalcular (padrão "optimistic numbering").
 */
@Component
class OsNumberSequencer {

  private final ServiceOrderRepository repo;

  OsNumberSequencer(ServiceOrderRepository repo) {
    this.repo = repo;
  }

  @Transactional(propagation = Propagation.MANDATORY)
  long nextFor(UUID branchId) {
    return repo.maxNumberByBranch(branchId) + 1L;
  }
}
