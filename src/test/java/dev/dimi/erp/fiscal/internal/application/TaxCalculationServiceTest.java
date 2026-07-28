package dev.dimi.erp.fiscal.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dimi.erp.cadastros.BranchView;
import dev.dimi.erp.cadastros.TaxRegime;
import dev.dimi.erp.fiscal.InvoiceDraft;
import dev.dimi.erp.fiscal.internal.domain.InvoiceItem;
import dev.dimi.erp.fiscal.internal.domain.TaxCalculation;
import dev.dimi.erp.fiscal.internal.domain.TaxType;
import dev.dimi.erp.fiscal.internal.persistence.TaxCalculationRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TaxCalculationServiceTest {

  private static final BigDecimal ICMS_RATE = new BigDecimal("18.00");

  @Mock private TaxCalculationRepository repository;
  private CstResolver cstResolver;
  private TaxRateLookup rates;
  private TaxCalculationService service;

  @BeforeEach
  void setUp() {
    cstResolver = new CstResolver();
    rates = new TaxRateLookup(ICMS_RATE);
    service = new TaxCalculationService(repository, cstResolver, rates);
    when(repository.save(any(TaxCalculation.class))).thenAnswer(inv -> inv.getArgument(0));
  }

  @Test
  void simplesCsosn102ZerosAllTaxes() {
    InvoiceItem item = item(new BigDecimal("100.00"));
    BranchView.FiscalView fiscal = fiscal(TaxRegime.SIMPLES, "102", null);

    TaxCalculationService.ItemResult result = service.computeForItem(item, fiscal);

    assertThat(result.totalTax()).isEqualByComparingTo("0.00");
    assertThat(result.draftTaxes()).hasSize(3);
    assertIcmsValue(result, "0.00");
    assertPis(result, "99", "0.00");
    assertCofins(result, "99", "0.00");
    verify(repository, times(3)).save(any(TaxCalculation.class));
  }

  @Test
  void presumidoCst00CalculatesIcmsPisCofins() {
    InvoiceItem item = item(new BigDecimal("1000.00"));
    BranchView.FiscalView fiscal = fiscal(TaxRegime.PRESUMIDO, null, "00");

    TaxCalculationService.ItemResult result = service.computeForItem(item, fiscal);

    // ICMS 18% de 1000 = 180.00; PIS 0.65% = 6.50; COFINS 3% = 30.00
    assertThat(result.totalTax()).isEqualByComparingTo("216.50");
    assertIcmsValue(result, "180.00");
    assertPis(result, "01", "6.50");
    assertCofins(result, "01", "30.00");
  }

  @Test
  void realCst40IsentaIcmsButPisCofinsDestacados() {
    InvoiceItem item = item(new BigDecimal("500.00"));
    BranchView.FiscalView fiscal = fiscal(TaxRegime.REAL, null, "40");

    TaxCalculationService.ItemResult result = service.computeForItem(item, fiscal);

    assertIcmsValue(result, "0.00");
    assertPis(result, "01", "3.25");
    assertCofins(result, "01", "15.00");
    assertThat(result.totalTax()).isEqualByComparingTo("18.25");
  }

  @Test
  void simplesExcedenteSegueCsosn() {
    InvoiceItem item = item(new BigDecimal("100.00"));
    BranchView.FiscalView fiscal = fiscal(TaxRegime.SIMPLES_EXCEDENTE, "500", null);

    TaxCalculationService.ItemResult result = service.computeForItem(item, fiscal);

    assertIcmsValue(result, "0.00");
    assertPis(result, "99", "0.00");
    assertThat(result.draftTaxes())
        .filteredOn(t -> t.tax() == TaxType.ICMS)
        .singleElement()
        .extracting(InvoiceDraft.DraftTax::cstCsosn)
        .isEqualTo("500");
  }

  // ---------------------------------------------------------------------------
  // helpers

  private static void assertIcmsValue(TaxCalculationService.ItemResult result, String expected) {
    assertThat(result.draftTaxes())
        .filteredOn(t -> t.tax() == TaxType.ICMS)
        .singleElement()
        .extracting(InvoiceDraft.DraftTax::value)
        .satisfies(v -> assertThat((BigDecimal) v).isEqualByComparingTo(expected));
  }

  private static void assertPis(
      TaxCalculationService.ItemResult result, String cst, String expectedValue) {
    InvoiceDraft.DraftTax pis =
        result.draftTaxes().stream().filter(t -> t.tax() == TaxType.PIS).findFirst().orElseThrow();
    assertThat(pis.cstCsosn()).isEqualTo(cst);
    assertThat(pis.value()).isEqualByComparingTo(expectedValue);
  }

  private static void assertCofins(
      TaxCalculationService.ItemResult result, String cst, String expectedValue) {
    InvoiceDraft.DraftTax cofins =
        result.draftTaxes().stream()
            .filter(t -> t.tax() == TaxType.COFINS)
            .findFirst()
            .orElseThrow();
    assertThat(cofins.cstCsosn()).isEqualTo(cst);
    assertThat(cofins.value()).isEqualByComparingTo(expectedValue);
  }

  private static InvoiceItem item(BigDecimal netValue) {
    InvoiceItem i =
        InvoiceItem.create(
            UUID.randomUUID(),
            1,
            UUID.randomUUID(),
            "SKU",
            "Produto teste",
            "12345678",
            null,
            "5102",
            "0",
            BigDecimal.ONE,
            "UN",
            "UN",
            netValue,
            netValue,
            BigDecimal.ZERO,
            netValue);
    // O id é setado em @PrePersist; pra testes unitários sem JPA, força via reflection.
    setField(i, "id", UUID.randomUUID());
    return i;
  }

  private static BranchView.FiscalView fiscal(TaxRegime regime, String csosn, String cst) {
    return new BranchView.FiscalView(
        "12345678000100", null, null, false, regime, csosn, cst, null, "HOMOLOGACAO", null);
  }

  private static void setField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
