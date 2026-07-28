package dev.dimi.erp.ordemservico;

import java.util.Optional;

/**
 * Porta de consulta pública de OS para módulos consumidores (fiscal em 9.2b).
 *
 * <p>Único caso de uso v1: o módulo fiscal carrega a OS pronta para faturamento via {@link
 * #findForInvoicing(ServiceOrderId)} para montar os drafts simultâneos de NFe (linhas de produto) e
 * NFSe (linhas de serviço). OSs em status não-faturáveis (ex.: {@code OPEN}, {@code CANCELLED}, ou
 * já {@code BILLED}) ficam fora da projeção — o caller (fiscal) deve sinalizar 422 nesses casos.
 */
public interface OsQueryPort {

  /**
   * Retorna a OS projetada para emissão fiscal se existir e estiver em status faturável. Devolve
   * {@link Optional#empty} caso contrário.
   */
  Optional<OsForInvoicingView> findForInvoicing(ServiceOrderId id);
}
