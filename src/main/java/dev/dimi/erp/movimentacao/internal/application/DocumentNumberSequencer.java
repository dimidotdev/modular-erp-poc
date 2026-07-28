package dev.dimi.erp.movimentacao.internal.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera o próximo {@code number} sequencial por filial+tabela. Implementação simples via {@code
 * SELECT COALESCE(MAX(number),0)+1} dentro da transação corrente. UNIQUE constraint na tabela
 * protege contra race condition — o caller deve capturar {@code DataIntegrityViolationException} e
 * recalcular se necessário (padrão "optimistic numbering").
 *
 * <p>v1 aceita a latência extra do retry esporádico em troca de simplicidade — sem tabela de
 * counter, sem sequence Postgres, sem coordenação fora do banco. Trocar por Postgres SEQUENCE
 * dedicada por (branch, document_type) se contention virar gargalo medível.
 */
@Component
class DocumentNumberSequencer {

  @PersistenceContext private EntityManager em;

  /**
   * Devolve o próximo número para uma tabela ({@code tableName}) numa filial. {@code tableName} vem
   * de constantes do caller — não aceitar input do usuário direto (SQL injection).
   */
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

  /**
   * Variante para tabelas que usam {@code origin_branch_id} (ex: {@code stock_transfer}). Slice 5.3
   * vai consumir.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public long nextForOriginBranch(String tableName, UUID originBranchId) {
    if (!ALLOWED_TABLES.contains(tableName)) {
      throw new IllegalArgumentException("tabela não permitida no sequencer: " + tableName);
    }
    Object result =
        em.createNativeQuery(
                "select coalesce(max(number), 0) + 1 from "
                    + tableName
                    + " where origin_branch_id = :b")
            .setParameter("b", originBranchId)
            .getSingleResult();
    return ((Number) result).longValue();
  }

  // Whitelist explícita — tableName vem de constantes do caller, mas reforçamos.
  private static final java.util.Set<String> ALLOWED_TABLES =
      java.util.Set.of(
          "sales_order",
          "sale",
          "stock_transfer",
          "outbound_voucher",
          "stock_inventory_adjustment");
}
