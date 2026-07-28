package dev.dimi.erp.fiscal.internal.application;

import dev.dimi.erp.fiscal.InvoiceOperation;
import org.springframework.stereotype.Component;

/**
 * Resolve CFOP da operação (RN-FIS-006). A implementação atual assume venda dentro da UF (5102) ou
 * compra dentro da UF (1102).
 *
 * <p>Refinamentos previstos:
 *
 * <ul>
 *   <li>Distinguir UF da filial e do destinatário (5102 vs 6102; 1102 vs 2102).
 *   <li>Tratar devolução, transferência e origem importada do SKU.
 * </ul>
 *
 * <p>Por ora, qualquer caller que precise de CFOP diferente do default deve sobrescrever no próprio
 * service; o controller atual não recebe override manual.
 */
@Component
public class CfopResolver {

  private static final String CFOP_SAIDA_MESMA_UF = "5102";
  private static final String CFOP_ENTRADA_MESMA_UF = "1102";

  /**
   * Default por operação assumindo mesma UF. Operações ainda não implementadas (TRANSFERENCIA,
   * AJUSTE, DEVOLUCAO_*) lançam até que as regras correspondentes sejam implementadas.
   */
  public String resolveDefault(InvoiceOperation operation) {
    return switch (operation) {
      case SAIDA -> CFOP_SAIDA_MESMA_UF;
      case ENTRADA -> CFOP_ENTRADA_MESMA_UF;
      case TRANSFERENCIA, AJUSTE, DEVOLUCAO_CLIENTE, DEVOLUCAO_FORNECEDOR ->
          throw new UnsupportedOperationException(
              "CFOP para " + operation + " entra no slice 8.6.");
    };
  }
}
