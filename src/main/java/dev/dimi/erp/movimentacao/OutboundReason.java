package dev.dimi.erp.movimentacao;

/**
 * Motivo de {@code outbound_voucher}. Determina CFOP padrão quando NFe é emitida (RN-MOV-022): 5910
 * (brinde), 5916 (amostra), 5949 (outros), etc — config aplicada no slice fiscal.
 */
public enum OutboundReason {
  SAMPLE,
  GIFT,
  WARRANTY,
  LOSS,
  OTHER
}
