package dev.dimi.erp.fiscal.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.dimi.erp.fiscal.InvoiceOperation;
import org.junit.jupiter.api.Test;

class CfopResolverTest {

  private final CfopResolver resolver = new CfopResolver();

  @Test
  void saidaDefaultIs5102() {
    assertThat(resolver.resolveDefault(InvoiceOperation.SAIDA)).isEqualTo("5102");
  }

  @Test
  void entradaDefaultIs1102() {
    assertThat(resolver.resolveDefault(InvoiceOperation.ENTRADA)).isEqualTo("1102");
  }

  @Test
  void deferredOperationsThrow() {
    assertThatThrownBy(() -> resolver.resolveDefault(InvoiceOperation.TRANSFERENCIA))
        .isInstanceOf(UnsupportedOperationException.class)
        .hasMessageContaining("8.6");
    assertThatThrownBy(() -> resolver.resolveDefault(InvoiceOperation.DEVOLUCAO_CLIENTE))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> resolver.resolveDefault(InvoiceOperation.DEVOLUCAO_FORNECEDOR))
        .isInstanceOf(UnsupportedOperationException.class);
    assertThatThrownBy(() -> resolver.resolveDefault(InvoiceOperation.AJUSTE))
        .isInstanceOf(UnsupportedOperationException.class);
  }
}
