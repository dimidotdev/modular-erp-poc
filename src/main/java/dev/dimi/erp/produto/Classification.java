package dev.dimi.erp.produto;

/**
 * Classificação fiscal/operacional do produto. RN-PRD-002: NCM é obrigatório para qualquer
 * classificação que não seja {@link #SERVICO}.
 */
public enum Classification {
  /** Mercadoria genérica para revenda. */
  PRODUTO,
  /** Serviço (sem estoque, sem NCM exigido). */
  SERVICO,
  /** Matéria-prima usada em produção. */
  MATERIA_PRIMA,
  /** Produto acabado fabricado pelo tenant. */
  PRODUTO_FINAL
}
