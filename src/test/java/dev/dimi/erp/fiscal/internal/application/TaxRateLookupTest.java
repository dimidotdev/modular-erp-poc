package dev.dimi.erp.fiscal.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class TaxRateLookupTest {

  @Test
  void respectsConfiguredIcmsRate() {
    TaxRateLookup lookup = new TaxRateLookup(new BigDecimal("17.00"));
    assertThat(lookup.icmsInternalRate()).isEqualByComparingTo("17.00");
  }

  @Test
  void pisCofinsAreFixed() {
    TaxRateLookup lookup = new TaxRateLookup(new BigDecimal("18.00"));
    assertThat(lookup.pisCumulativoRate()).isEqualByComparingTo("0.65");
    assertThat(lookup.cofinsCumulativoRate()).isEqualByComparingTo("3.00");
  }

  @Test
  void exposesPublicConstants() {
    assertThat(TaxRateLookup.PIS_CUMULATIVO_RATE).isEqualByComparingTo("0.65");
    assertThat(TaxRateLookup.COFINS_CUMULATIVO_RATE).isEqualByComparingTo("3.00");
  }
}
