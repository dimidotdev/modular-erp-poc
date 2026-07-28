package dev.dimi.erp.shared.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CnpjTest {

  @Nested
  @DisplayName("Cnpj(valido)")
  class Valido {

    @Test
    void cnpjSemMascaraComDvCorretoDeveAceitar() {
      Cnpj cnpj = Cnpj.of("11222333000181");
      assertThat(cnpj.value()).isEqualTo("11222333000181");
    }

    @Test
    void cnpjComMascaraDeveArmazenarSemMascara() {
      Cnpj cnpj = Cnpj.of("11.222.333/0001-81");
      assertThat(cnpj.value()).isEqualTo("11222333000181");
    }

    @Test
    void cnpjTodoZeroDeveAceitarComoSentinela() {
      Cnpj cnpj = Cnpj.of("00000000000000");
      assertThat(cnpj.value()).isEqualTo("00000000000000");
    }

    @Test
    void formattedDeveProduzirMascaraCanonica() {
      assertThat(Cnpj.of("11222333000181").formatted()).isEqualTo("11.222.333/0001-81");
    }
  }

  @Nested
  @DisplayName("Cnpj(invalido)")
  class Invalido {

    @Test
    void cnpjComMenosDe14DigitosDeveLancar() {
      assertThatThrownBy(() -> Cnpj.of("123"))
          .isInstanceOf(InvalidCnpjException.class)
          .hasFieldOrPropertyWithValue("code", "documents.invalid-cnpj");
    }

    @Test
    void cnpjComDigitoVerificadorErradoDeveLancar() {
      assertThatThrownBy(() -> Cnpj.of("11222333000182")).isInstanceOf(InvalidCnpjException.class);
    }

    @Test
    void cnpjComTodosDigitosRepetidosNaoZeroDeveLancar() {
      assertThatThrownBy(() -> Cnpj.of("11111111111111")).isInstanceOf(InvalidCnpjException.class);
      assertThatThrownBy(() -> Cnpj.of("99999999999999")).isInstanceOf(InvalidCnpjException.class);
    }
  }
}
