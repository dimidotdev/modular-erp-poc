package dev.dimi.erp.fiscal.internal.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dimi.erp.cadastros.TaxRegime;
import dev.dimi.erp.fiscal.internal.application.CstResolver.Result;
import org.junit.jupiter.api.Test;

class CstResolverTest {

  private final CstResolver resolver = new CstResolver();

  @Test
  void simplesUsesCsosnDefault() {
    Result r = resolver.resolveIcms(TaxRegime.SIMPLES, "102", "00");
    assertThat(r.mode()).isEqualTo(Result.Mode.CSOSN);
    assertThat(r.code()).isEqualTo("102");
    assertThat(r.calculatesIcms()).isFalse();
  }

  @Test
  void simplesExcedenteUsesCsosn() {
    Result r = resolver.resolveIcms(TaxRegime.SIMPLES_EXCEDENTE, "500", null);
    assertThat(r.mode()).isEqualTo(Result.Mode.CSOSN);
    assertThat(r.code()).isEqualTo("500");
    assertThat(r.calculatesIcms()).isFalse();
  }

  @Test
  void meiUsesCsosnFallback102() {
    Result r = resolver.resolveIcms(TaxRegime.MEI, null, null);
    assertThat(r.mode()).isEqualTo(Result.Mode.CSOSN);
    assertThat(r.code()).isEqualTo("102");
  }

  @Test
  void presumidoCst00CalculatesIcms() {
    Result r = resolver.resolveIcms(TaxRegime.PRESUMIDO, null, "00");
    assertThat(r.mode()).isEqualTo(Result.Mode.CST_ICMS);
    assertThat(r.code()).isEqualTo("00");
    assertThat(r.calculatesIcms()).isTrue();
  }

  @Test
  void realCst20CalculatesIcms() {
    Result r = resolver.resolveIcms(TaxRegime.REAL, null, "20");
    assertThat(r.calculatesIcms()).isTrue();
  }

  @Test
  void realCst40DoesNotCalculateIcms() {
    Result r = resolver.resolveIcms(TaxRegime.REAL, null, "40");
    assertThat(r.calculatesIcms()).isFalse();
  }

  @Test
  void realCst60DoesNotCalculateIcms() {
    Result r = resolver.resolveIcms(TaxRegime.REAL, null, "60");
    assertThat(r.calculatesIcms()).isFalse();
  }

  @Test
  void presumidoFallbackTo00WhenDefaultMissing() {
    Result r = resolver.resolveIcms(TaxRegime.PRESUMIDO, null, null);
    assertThat(r.code()).isEqualTo("00");
    assertThat(r.calculatesIcms()).isTrue();
  }

  @Test
  void destacaPisCofinsOnlyForPresumidoAndReal() {
    assertThat(resolver.destacaPisCofins(TaxRegime.PRESUMIDO)).isTrue();
    assertThat(resolver.destacaPisCofins(TaxRegime.REAL)).isTrue();
    assertThat(resolver.destacaPisCofins(TaxRegime.SIMPLES)).isFalse();
    assertThat(resolver.destacaPisCofins(TaxRegime.SIMPLES_EXCEDENTE)).isFalse();
    assertThat(resolver.destacaPisCofins(TaxRegime.MEI)).isFalse();
  }
}
