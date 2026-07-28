package dev.dimi.erp.compras.internal.application;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera próximo {@code number} por filial+tabela em {@code compras}. Mesmo padrão de {@code
 * DocumentNumberSequencer} do módulo movimentação — duplicado aqui pois movimentação não faz parte
 * das {@code allowedDependencies} de compras (e nem deveria, são módulos pares).
 *
 * <p>Whitelist de tabelas permitidas previne SQL injection. UNIQUE constraint no banco protege race
 * conditions — caller usa retry pattern.
 */
@Component
class PurchaseNumberSequencer {

  private static final Set<String> ALLOWED_TABLES =
      Set.of(
          "purchase_request", "quotation", "purchase_order", "purchase_receipt", "supplier_return");

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

  /**
   * Variante para purchase_receipt + supplier_return, que filtram por {@code order_id} ou {@code
   * receipt_id} (não branch_id). Slice 6.3 consome.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public long nextForParent(String tableName, String parentColumn, UUID parentId) {
    if (!ALLOWED_TABLES.contains(tableName)) {
      throw new IllegalArgumentException("tabela não permitida no sequencer: " + tableName);
    }
    if (!Set.of("order_id", "receipt_id").contains(parentColumn)) {
      throw new IllegalArgumentException("parentColumn não permitida: " + parentColumn);
    }
    Object result =
        em.createNativeQuery(
                "select coalesce(max(number), 0) + 1 from "
                    + tableName
                    + " where "
                    + parentColumn
                    + " = :p")
            .setParameter("p", parentId)
            .getSingleResult();
    return ((Number) result).longValue();
  }
}
