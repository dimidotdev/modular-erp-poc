package dev.dimi.erp.financeiro.internal.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera próximo {@code number} por filial+tabela em {@code financeiro}. Mesmo padrão de {@code
 * PurchaseNumberSequencer} e {@code DocumentNumberSequencer} de outros módulos — duplicação
 * intencional pois módulos pares não devem importar internals uns dos outros.
 *
 * <p>Whitelist de tabelas previne SQL injection. UNIQUE constraint no banco protege race conditions
 * — caller usa retry pattern em caso de {@code DataIntegrityViolationException}.
 */
@Component
class FinancialNumberSequencer {

  private static final Set<String> ALLOWED_TABLES = Set.of("financial_record");

  @PersistenceContext private EntityManager em;

  @Transactional(propagation = Propagation.MANDATORY)
  public long nextFor(String tableName, UUID branchId) {
    if (!ALLOWED_TABLES.contains(tableName)) {
      throw new IllegalArgumentException("tabela não permitida no sequencer: " + tableName);
    }
    Object result =
        em.createNativeQuery(
                "select coalesce(max(number), 0) + 1 from " + tableName + " where branch_id = :b")
            .setParameter("b", branchId)
            .getSingleResult();
    return ((Number) result).longValue();
  }
}
