package dev.dimi.erp.fiscal;

/** Operação fiscal — base do cálculo de CFOP padrão (RN-FIS-006). */
public enum InvoiceOperation {
  SAIDA,
  ENTRADA,
  TRANSFERENCIA,
  AJUSTE,
  DEVOLUCAO_CLIENTE,
  DEVOLUCAO_FORNECEDOR
}
