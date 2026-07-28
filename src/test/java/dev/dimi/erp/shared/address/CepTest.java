package dev.dimi.erp.shared.address;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CepTest {

  @Test
  void cepSemMascaraDeveAceitar() {
    Cep cep = Cep.of("01310100");
    assertThat(cep.value()).isEqualTo("01310100");
  }

  @Test
  void cepComMascaraDeveArmazenarSemMascara() {
    Cep cep = Cep.of("01310-100");
    assertThat(cep.value()).isEqualTo("01310100");
  }

  @Test
  void cepFormattedDeveProduzirMascaraCanonica() {
    assertThat(Cep.of("01310100").formatted()).isEqualTo("01310-100");
  }

  @Test
  void cepComMenosDe8DigitosDeveLancar() {
    assertThatThrownBy(() -> Cep.of("12345"))
        .isInstanceOf(InvalidCepException.class)
        .hasFieldOrPropertyWithValue("code", "address.invalid-cep");
  }

  @Test
  void cepComMaisDe8DigitosDeveLancar() {
    assertThatThrownBy(() -> Cep.of("123456789")).isInstanceOf(InvalidCepException.class);
  }
}
