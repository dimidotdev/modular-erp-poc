package dev.dimi.erp.shared.documents;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class CpfTest {

  @Nested
  @DisplayName("Cpf(valido)")
  class Valido {

    @Test
    void cpfSemMascaraComDvCorretoDeveAceitar() {
      Cpf cpf = Cpf.of("11144477735");
      assertThat(cpf.value()).isEqualTo("11144477735");
    }

    @Test
    void cpfComMascaraDeveArmazenarSemMascara() {
      Cpf cpf = Cpf.of("111.444.777-35");
      assertThat(cpf.value()).isEqualTo("11144477735");
    }

    @Test
    void cpfTodoZeroDeveAceitarComoSentinelaConsumidorFinal() {
      Cpf cpf = Cpf.of("00000000000");
      assertThat(cpf.value()).isEqualTo("00000000000");
    }

    @Test
    void formattedDeveProduzirMascaraCanonica() {
      assertThat(Cpf.of("11144477735").formatted()).isEqualTo("111.444.777-35");
    }
  }

  @Nested
  @DisplayName("Cpf(invalido)")
  class Invalido {

    @Test
    void cpfComMenosDe11DigitosDeveLancar() {
      assertThatThrownBy(() -> Cpf.of("123"))
          .isInstanceOf(InvalidCpfException.class)
          .hasFieldOrPropertyWithValue("code", "documents.invalid-cpf");
    }

    @Test
    void cpfComMaisDe11DigitosDeveLancar() {
      assertThatThrownBy(() -> Cpf.of("111444777350000")).isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void cpfComDigitoVerificadorErradoDeveLancar() {
      assertThatThrownBy(() -> Cpf.of("11144477736")).isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void cpfComTodosDigitosRepetidosNaoZeroDeveLancar() {
      assertThatThrownBy(() -> Cpf.of("11111111111")).isInstanceOf(InvalidCpfException.class);
      assertThatThrownBy(() -> Cpf.of("99999999999")).isInstanceOf(InvalidCpfException.class);
    }

    @Test
    void cpfComLetrasNoMeioDeveLancarPorTamanho() {
      assertThatThrownBy(() -> Cpf.of("111.AAA.777-35")).isInstanceOf(InvalidCpfException.class);
    }
  }
}
