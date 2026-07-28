package dev.dimi.erp.financeiro;

/**
 * Tipo de {@code bank_account}: CASH = caixa físico; BANK = conta bancária real; CARD =
 * maquininha/credenciador; GATEWAY = adquirente de pagamento online; VOUCHER =
 * vale-refeição/alimentação.
 */
public enum BankAccountType {
  CASH,
  BANK,
  CARD,
  GATEWAY,
  VOUCHER,
}
