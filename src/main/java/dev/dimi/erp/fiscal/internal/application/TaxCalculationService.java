package dev.dimi.erp.fiscal.internal.application;

import dev.dimi.erp.cadastros.BranchView;
import dev.dimi.erp.fiscal.InvoiceDraft;
import dev.dimi.erp.fiscal.internal.application.CstResolver.Result;
import dev.dimi.erp.fiscal.internal.domain.InvoiceItem;
import dev.dimi.erp.fiscal.internal.domain.TaxCalculation;
import dev.dimi.erp.fiscal.internal.domain.TaxType;
import dev.dimi.erp.fiscal.internal.persistence.TaxCalculationRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula ICMS/PIS/COFINS por {@link InvoiceItem} conforme regime tributário da filial. Persiste
 * uma row {@link TaxCalculation} por imposto destacado e retorna o {@link Result} com a soma para
 * popular {@code invoice.total_taxes} + os {@code DraftTax} para o adapter fiscal.
 *
 * <p>Cobertura v1 (~85% das vendas comuns intraestaduais):
 *
 * <ul>
 *   <li><b>Simples Nacional</b> (CSOSN 102/103/400/500): ICMS=0 (recolhe via DAS); PIS=0/CST 99;
 *       COFINS=0/CST 99. Linhas TaxCalculation são persistidas mesmo com valores zerados para
 *       rastreabilidade no SPED.
 *   <li><b>Lucro Real/Presumido</b> (CST 00/20/40/60): ICMS calculado (CST 00 direto; CST 20 com
 *       redução de base — v1 usa 0% de redução); PIS 0,65% + COFINS 3% cumulativo, CST 01.
 * </ul>
 *
 * <p>Cortes deliberados (v2+): ST/FCP/IPI/ISSQN, regimes não-cumulativos, "tese do século"
 * (exclusão do ICMS da base PIS/COFINS), origem importada, DIFAL interestadual.
 */
@Service
@RequiredArgsConstructor
public class TaxCalculationService {

  private static final BigDecimal ONE_HUNDRED = new BigDecimal("100");

  /** CST PIS/COFINS para operação tributável com alíquota básica (cumulativo Real/Presumido). */
  private static final String PIS_COFINS_CST_TRIBUTADA = "01";

  /** CST PIS/COFINS para Simples Nacional (recolhido via DAS). */
  private static final String PIS_COFINS_CST_SIMPLES = "99";

  private final TaxCalculationRepository repository;
  private final CstResolver cstResolver;
  private final TaxRateLookup rates;

  /**
   * Calcula impostos do item persistido, salva as rows {@link TaxCalculation} e devolve o {@link
   * ItemResult} com totalImposto + draft taxes prontos para alimentar {@link InvoiceDraft}.
   *
   * <p>Executa em {@link Propagation#MANDATORY} — sempre dentro da transação de emissão do caller;
   * rollback do caller também desfaz as rows de imposto.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public ItemResult computeForItem(InvoiceItem item, BranchView.FiscalView fiscal) {
    BigDecimal base = item.getNetValue();
    List<TaxCalculation> persisted = new ArrayList<>();
    List<InvoiceDraft.DraftTax> draftTaxes = new ArrayList<>();
    BigDecimal totalTax = BigDecimal.ZERO;

    Result icms =
        cstResolver.resolveIcms(fiscal.taxRegime(), fiscal.csosnDefault(), fiscal.cstIcmsDefault());

    // --- ICMS ---
    BigDecimal icmsBase = base;
    BigDecimal icmsRate = icms.calculatesIcms() ? rates.icmsInternalRate() : BigDecimal.ZERO;
    BigDecimal icmsValue =
        icmsRate.signum() == 0
            ? BigDecimal.ZERO
            : roundCurrency(
                icmsBase.multiply(icmsRate).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP));
    persisted.add(
        repository.save(
            TaxCalculation.create(
                item.getId(),
                TaxType.ICMS,
                icms.code(),
                icmsBase,
                icmsRate,
                icmsValue,
                null,
                null,
                null,
                null,
                null,
                null)));
    draftTaxes.add(
        new InvoiceDraft.DraftTax(
            TaxType.ICMS, icms.code(), icmsBase, icmsRate, icmsValue, null, null, null));
    totalTax = totalTax.add(icmsValue);

    // --- PIS / COFINS ---
    boolean destaca = cstResolver.destacaPisCofins(fiscal.taxRegime());
    String pisCofinsCst = destaca ? PIS_COFINS_CST_TRIBUTADA : PIS_COFINS_CST_SIMPLES;
    BigDecimal pisRate = destaca ? rates.pisCumulativoRate() : BigDecimal.ZERO;
    BigDecimal cofinsRate = destaca ? rates.cofinsCumulativoRate() : BigDecimal.ZERO;
    BigDecimal pisValue =
        pisRate.signum() == 0
            ? BigDecimal.ZERO
            : roundCurrency(base.multiply(pisRate).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP));
    BigDecimal cofinsValue =
        cofinsRate.signum() == 0
            ? BigDecimal.ZERO
            : roundCurrency(base.multiply(cofinsRate).divide(ONE_HUNDRED, 4, RoundingMode.HALF_UP));

    persisted.add(
        repository.save(
            TaxCalculation.create(
                item.getId(),
                TaxType.PIS,
                pisCofinsCst,
                base,
                pisRate,
                pisValue,
                null,
                null,
                null,
                null,
                null,
                null)));
    draftTaxes.add(
        new InvoiceDraft.DraftTax(
            TaxType.PIS, pisCofinsCst, base, pisRate, pisValue, null, null, null));
    totalTax = totalTax.add(pisValue);

    persisted.add(
        repository.save(
            TaxCalculation.create(
                item.getId(),
                TaxType.COFINS,
                pisCofinsCst,
                base,
                cofinsRate,
                cofinsValue,
                null,
                null,
                null,
                null,
                null,
                null)));
    draftTaxes.add(
        new InvoiceDraft.DraftTax(
            TaxType.COFINS, pisCofinsCst, base, cofinsRate, cofinsValue, null, null, null));
    totalTax = totalTax.add(cofinsValue);

    return new ItemResult(totalTax, draftTaxes, persisted);
  }

  private BigDecimal roundCurrency(BigDecimal v) {
    return v.setScale(2, RoundingMode.HALF_UP);
  }

  /**
   * Resultado do cálculo para um {@link InvoiceItem}: soma de impostos para agregar em {@code
   * invoice.total_taxes}, os draft taxes prontos para o {@link InvoiceDraft.DraftItem}, e a lista
   * de {@link TaxCalculation} persistida (útil para asserts em testes).
   */
  public record ItemResult(
      BigDecimal totalTax,
      List<InvoiceDraft.DraftTax> draftTaxes,
      List<TaxCalculation> persisted) {}
}
