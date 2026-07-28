package dev.dimi.erp.movimentacao;

import java.util.Optional;

/**
 * Porta de consulta pública de vendas para módulos consumidores (fiscal, contratos futuros).
 *
 * <p>O módulo fiscal carrega a venda fechada via {@link #findForInvoicing(SaleId)} para montar a
 * NFe correspondente. Vendas abertas não aparecem na projeção, pois a emissão fiscal exige status
 * {@link SaleStatus#CLOSED}.
 */
public interface SaleQueryPort {

  /**
   * Retorna a venda projetada para emissão fiscal se existir e estiver em {@link
   * SaleStatus#CLOSED}. Devolve {@link Optional#empty} para vendas inexistentes ou em qualquer
   * outro status (OPEN, CANCELLED, etc) — o caller (fiscal) deve sinalizar 422 nesses casos.
   */
  Optional<SaleForInvoicingView> findForInvoicing(SaleId id);
}
