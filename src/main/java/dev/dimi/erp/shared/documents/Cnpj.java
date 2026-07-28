package dev.dimi.erp.shared.documents;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * CNPJ — Cadastro Nacional da Pessoa Jurídica.
 *
 * <p>14 dígitos com dois dígitos verificadores (DV) calculados módulo 11 com pesos {@code
 * 5,4,3,2,9,8,7,6,5,4,3,2} e {@code 6,5,4,3,2,9,8,7,6,5,4,3,2}. A entrada aceita máscara ({@code
 * 12.345.678/0001-90}) ou apenas dígitos; o valor armazenado e serializado é sempre sem máscara.
 *
 * <p>{@code 00000000000000} é aceito como sentinela. Demais sequências de dígitos repetidos ({@code
 * 11..1}..{@code 99..9}) são rejeitadas.
 */
public record Cnpj(@JsonValue String value) {

  private static final int[] WEIGHTS_DV1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
  private static final int[] WEIGHTS_DV2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};

  public Cnpj {
    Objects.requireNonNull(value, "value");
    String digits = value.replaceAll("\\D", "");
    if (digits.length() != 14) {
      throw new InvalidCnpjException(value, "CNPJ deve conter 14 dígitos.");
    }
    if (isRepeatedNonZero(digits)) {
      throw new InvalidCnpjException(value, "CNPJ com dígitos repetidos inválido.");
    }
    if (!isValidDv(digits)) {
      throw new InvalidCnpjException(value, "Dígitos verificadores do CNPJ inválidos.");
    }
    value = digits;
  }

  @JsonCreator
  public static Cnpj of(String raw) {
    return new Cnpj(raw);
  }

  /**
   * Versão formatada para apresentação: {@code 12.345.678/0001-90}. Persistir sempre {@link
   * #value}.
   */
  public String formatted() {
    return value.substring(0, 2)
        + "."
        + value.substring(2, 5)
        + "."
        + value.substring(5, 8)
        + "/"
        + value.substring(8, 12)
        + "-"
        + value.substring(12);
  }

  private static boolean isRepeatedNonZero(String digits) {
    char first = digits.charAt(0);
    if (first == '0') {
      return false;
    }
    for (int i = 1; i < digits.length(); i++) {
      if (digits.charAt(i) != first) {
        return false;
      }
    }
    return true;
  }

  private static boolean isValidDv(String digits) {
    int dv1 = calculateDv(digits, WEIGHTS_DV1);
    if (Character.getNumericValue(digits.charAt(12)) != dv1) {
      return false;
    }
    int dv2 = calculateDv(digits, WEIGHTS_DV2);
    return Character.getNumericValue(digits.charAt(13)) == dv2;
  }

  private static int calculateDv(String digits, int[] weights) {
    int sum = 0;
    for (int i = 0; i < weights.length; i++) {
      sum += Character.getNumericValue(digits.charAt(i)) * weights[i];
    }
    int mod = sum % 11;
    return mod < 2 ? 0 : 11 - mod;
  }
}
