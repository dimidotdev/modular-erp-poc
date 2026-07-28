package dev.dimi.erp.fiscal.internal.application;

import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolve alíquotas tributárias do MVP v1 a partir de {@code application.yml}. Não consulta tabela
 * por UF nem {@code branch_fiscal} no slice 8.3a — a alíquota interna do ICMS é configuração global
 * do tenant via propriedade {@code erp-poc.fiscal.tax.icms.default-rate}, com fallback conservador
 * de 18%.
 *
 * <p>PIS e COFINS no regime cumulativo são alíquotas fixas legais (Lei 9.718/1998): 0,65% e 3,00% —
 * não configuráveis.
 *
 * <p>Refino 8.6+: alíquota por UF do branch + override por NCM via tabela versionada.
 */
@Component
public class TaxRateLookup {

  /** Alíquota PIS cumulativo (fixa por lei). */
  public static final BigDecimal PIS_CUMULATIVO_RATE = new BigDecimal("0.65");

  /** Alíquota COFINS cumulativo (fixa por lei). */
  public static final BigDecimal COFINS_CUMULATIVO_RATE = new BigDecimal("3.00");

  private final BigDecimal defaultIcmsRate;

  public TaxRateLookup(
      @Value("${erp-poc.fiscal.tax.icms.default-rate:18.00}") BigDecimal defaultIcmsRate) {
    this.defaultIcmsRate = defaultIcmsRate;
  }

  /** Alíquota interna de ICMS aplicada quando o CST exige destaque (CST 00, CST 20). */
  public BigDecimal icmsInternalRate() {
    return defaultIcmsRate;
  }

  public BigDecimal pisCumulativoRate() {
    return PIS_CUMULATIVO_RATE;
  }

  public BigDecimal cofinsCumulativoRate() {
    return COFINS_CUMULATIVO_RATE;
  }
}
