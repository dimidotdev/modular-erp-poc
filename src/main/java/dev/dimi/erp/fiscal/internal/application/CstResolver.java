package dev.dimi.erp.fiscal.internal.application;

import dev.dimi.erp.cadastros.TaxRegime;
import org.springframework.stereotype.Component;

/**
 * Resolve o par (modo, código) a partir do {@link TaxRegime} da filial: Simples Nacional usa {@code
 * CSOSN} (3 dígitos); Lucro Real/Presumido e MEI usam {@code CST} (2 dígitos prefixados com a
 * origem). Slice 8.3a v1 sem override por item — o código vem do default da filial em {@code
 * BranchView.FiscalView.csosnDefault} / {@code cstIcmsDefault}.
 *
 * <p>Cobertura do v1 (~85% das vendas comuns):
 *
 * <ul>
 *   <li><b>CSOSN</b> (Simples): {@code 102}, {@code 103}, {@code 400}, {@code 500}.
 *   <li><b>CST ICMS</b> (Real/Presumido): {@code 00}, {@code 20}, {@code 40}, {@code 60}.
 * </ul>
 *
 * <p>ST/FCP/IPI/ISSQN e regimes especiais ficam para slice posterior conforme cortes do 8.3.
 */
@Component
public class CstResolver {

  /** Resultado da resolução com flag para o caller saber se deve calcular ICMS ou pular. */
  public record Result(Mode mode, String code, boolean calculatesIcms) {

    public enum Mode {
      CSOSN,
      CST_ICMS
    }
  }

  /**
   * @param regime regime tributário da filial (vem de {@code BranchView.FiscalView.taxRegime})
   * @param csosnDefault default do Simples (ex {@code "102"})
   * @param cstIcmsDefault default do Lucro (ex {@code "00"})
   */
  public Result resolveIcms(TaxRegime regime, String csosnDefault, String cstIcmsDefault) {
    return switch (regime) {
      case SIMPLES, SIMPLES_EXCEDENTE, MEI -> {
        String code = csosnDefault == null ? "102" : csosnDefault;
        // CSOSN suportados no v1 (102, 103, 400, 500) nunca destacam ICMS próprio — Simples
        // recolhe via DAS; 500 é ST cobrado anteriormente. Refino com 101 (crédito) em v2.
        yield new Result(Result.Mode.CSOSN, code, false);
      }
      case PRESUMIDO, REAL -> {
        String code = cstIcmsDefault == null ? "00" : cstIcmsDefault;
        yield new Result(Result.Mode.CST_ICMS, code, calculatesIcmsForCst(code));
      }
    };
  }

  /**
   * Indica se o regime destaca PIS/COFINS na NFe. SIMPLES* e MEI não destacam (recolhem via DAS);
   * PRESUMIDO/REAL destacam no regime cumulativo (CST 01).
   */
  public boolean destacaPisCofins(TaxRegime regime) {
    return regime == TaxRegime.PRESUMIDO || regime == TaxRegime.REAL;
  }

  /** Códigos CST suportados no v1 — 00 e 20 calculam; 40 isento; 60 ST já cobrado. */
  boolean calculatesIcmsForCst(String cst) {
    return "00".equals(cst) || "020".equals(cst) || "20".equals(cst);
  }
}
