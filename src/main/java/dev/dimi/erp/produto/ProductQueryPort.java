package dev.dimi.erp.produto;

import java.util.Optional;

/**
 * Consultas read-only sobre produto/embalagem expostas para outros módulos (fiscal, movimentação,
 * ordem-servico). Implementação interna no módulo. Métodos retornam {@link Optional} em vez de
 * lançar — o consumidor decide se a ausência é erro.
 */
public interface ProductQueryPort {

  /**
   * Snapshot fiscal/identidade de uma embalagem. Retorna {@code empty} se inativa ou inexistente.
   */
  Optional<PackagingSnapshot> findPackaging(PackagingId packagingId);

  /** Verifica existência ativa — útil para validar input antes de transação cara. */
  boolean isActivePackaging(PackagingId packagingId);

  /**
   * Dados mínimos da embalagem expostos cross-module. Inclui o que NFe/estoque precisam sem
   * carregar a entidade JPA. Strings podem ser {@code null} (barcode, cest, ncm) — produto só exige
   * NCM quando classificação ≠ SERVICO (RN-PRD-002).
   */
  record PackagingSnapshot(
      PackagingId packagingId,
      ProductId productId,
      String sku,
      String barcode,
      String productName,
      Classification classification,
      String ncm,
      String cest,
      String origin,
      boolean active) {}
}
