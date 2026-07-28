package dev.dimi.erp.shared.address;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * CEP — Código de Endereçamento Postal.
 *
 * <p>8 dígitos, sem dígito verificador (a validação de existência real fica para o slice 3.4 com
 * integração ViaCEP). A entrada aceita máscara ({@code 01310-100}) ou apenas dígitos; o valor
 * armazenado e serializado é sempre sem máscara.
 */
public record Cep(@JsonValue String value) {

  public Cep {
    Objects.requireNonNull(value, "value");
    String digits = value.replaceAll("\\D", "");
    if (digits.length() != 8) {
      throw new InvalidCepException(value, "CEP deve conter 8 dígitos.");
    }
    value = digits;
  }

  @JsonCreator
  public static Cep of(String raw) {
    return new Cep(raw);
  }

  /** Versão formatada para apresentação: {@code 01310-100}. Persistir sempre {@link #value}. */
  public String formatted() {
    return value.substring(0, 5) + "-" + value.substring(5);
  }
}
