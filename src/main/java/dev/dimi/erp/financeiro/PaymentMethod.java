package dev.dimi.erp.financeiro;

/**
 * Forma de pagamento. v1 cobre os principais meios brasileiros; OTHER cobre casos raros (vale,
 * compensação).
 */
public enum PaymentMethod {
  CASH,
  PIX,
  BOLETO,
  TRANSFER,
  CARD_DEBIT,
  CARD_CREDIT,
  CHECK,
  OTHER,
}
