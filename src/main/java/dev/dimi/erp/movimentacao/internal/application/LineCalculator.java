package dev.dimi.erp.movimentacao.internal.application;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Cálculos de linha e totais centralizados — pedido e venda compartilham regra. Todas as operações
 * usam HALF_UP em 2 casas para valores monetários, exceto {@code grossValue}/{@code discountValue}
 * que carregam até a precisão completa de price × qty antes de arredondar.
 *
 * <p>RN-PRD-005 (discount_max enforcement) NÃO é aplicado aqui — fica no service que carrega o
 * contexto da tabela de preço.
 */
final class LineCalculator {

  private LineCalculator() {}

  /** Valor bruto = quantity × unitPrice (não-negativo). */
  static BigDecimal grossValue(BigDecimal quantity, BigDecimal unitPrice) {
    return quantity.multiply(unitPrice).setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Valor de desconto a partir de fração ({@code 0.0500} = 5%). Quando {@code discountPct} é null
   * ou zero, devolve zero.
   */
  static BigDecimal discountValueFromPct(BigDecimal grossValue, BigDecimal discountPct) {
    if (discountPct == null || discountPct.signum() == 0) {
      return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }
    return grossValue.multiply(discountPct).setScale(2, RoundingMode.HALF_UP);
  }

  /** Valor líquido = bruto - desconto (clamp em zero pra segurança). */
  static BigDecimal netValue(BigDecimal grossValue, BigDecimal discountValue) {
    BigDecimal net = grossValue.subtract(discountValue);
    return net.signum() < 0 ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP) : net;
  }
}
