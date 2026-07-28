package dev.dimi.erp.fiscal;

/**
 * Indicador de pagamento da NFe — tag indPag. **DIFERENTE** de {@code financeiro.PaymentMethod}
 * (CASH/PIX/BOLETO/etc) — aqui é apenas o discriminador fiscal exigido pela SEFAZ.
 */
public enum InvoicePaymentMethod {
  AVISTA,
  APRAZO,
  OUTRO
}
