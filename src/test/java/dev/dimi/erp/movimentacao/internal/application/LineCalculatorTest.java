package dev.dimi.erp.movimentacao.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Acessa {@link LineCalculator} via reflection — package-private + final intencional para manter o
 * helper invisível fora do módulo.
 */
class LineCalculatorTest {

  private static BigDecimal gross(String qty, String price) throws Exception {
    var m =
        LineCalculator.class.getDeclaredMethod("grossValue", BigDecimal.class, BigDecimal.class);
    m.setAccessible(true);
    return (BigDecimal) m.invoke(null, new BigDecimal(qty), new BigDecimal(price));
  }

  private static BigDecimal discount(String gross, String pct) throws Exception {
    var m =
        LineCalculator.class.getDeclaredMethod(
            "discountValueFromPct", BigDecimal.class, BigDecimal.class);
    m.setAccessible(true);
    return (BigDecimal)
        m.invoke(null, new BigDecimal(gross), pct == null ? null : new BigDecimal(pct));
  }

  private static BigDecimal net(String gross, String discount) throws Exception {
    var m = LineCalculator.class.getDeclaredMethod("netValue", BigDecimal.class, BigDecimal.class);
    m.setAccessible(true);
    return (BigDecimal) m.invoke(null, new BigDecimal(gross), new BigDecimal(discount));
  }

  @Test
  @DisplayName("gross = qty × price com HALF_UP em 2 casas")
  void grossRedondeia() throws Exception {
    assertThat(gross("3", "10.555")).isEqualByComparingTo("31.67");
    assertThat(gross("1", "0")).isEqualByComparingTo("0.00");
    assertThat(gross("2.5", "100.00")).isEqualByComparingTo("250.00");
  }

  @Test
  @DisplayName("discount null ou zero devolve zero formatado")
  void discountNullOuZero() throws Exception {
    assertThat(discount("100.00", null)).isEqualByComparingTo("0.00");
    assertThat(discount("100.00", "0")).isEqualByComparingTo("0.00");
  }

  @Test
  @DisplayName("discount 0.10 sobre 100 = 10.00")
  void discountFracao() throws Exception {
    assertThat(discount("100.00", "0.10")).isEqualByComparingTo("10.00");
    assertThat(discount("99.90", "0.05")).isEqualByComparingTo("5.00"); // 4.995 → 5.00 HALF_UP
  }

  @Test
  @DisplayName("net = gross - discount, clamp em zero se ficaria negativo")
  void netClampNegativo() throws Exception {
    assertThat(net("100.00", "30.00")).isEqualByComparingTo("70.00");
    assertThat(net("10.00", "15.00")).isEqualByComparingTo("0.00");
    assertThat(net("100.00", "100.00")).isEqualByComparingTo("0.00");
  }
}
