package dev.dimi.erp.cadastros.internal.adapter;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;

class LocalFsCertificateMaterialStoreTest {

  @TempDir Path tempDir;

  @Test
  void rejeitaArmazenamentoSemProfileLocalOuTest() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("production");

    assertThatThrownBy(
            () ->
                new LocalFsCertificateMaterialStore(
                    tempDir.toString(), new ObjectMapper(), environment))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("local/test");
  }

  @Test
  void permiteArmazenamentoNoProfileTest() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("test");

    assertThatCode(
            () ->
                new LocalFsCertificateMaterialStore(
                    tempDir.toString(), new ObjectMapper(), environment))
        .doesNotThrowAnyException();
  }

  @Test
  void rejeitaNomeQuePoderiaEscaparDoDiretorio() {
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("test");
    var store =
        new LocalFsCertificateMaterialStore(tempDir.toString(), new ObjectMapper(), environment);

    assertThatThrownBy(() -> store.get("..\\outside"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("secretName inválido");
  }
}
