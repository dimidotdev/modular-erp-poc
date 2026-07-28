package dev.dimi.erp.shared.documents;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Objects;

/**
 * CPF — Cadastro de Pessoa Física.
 *
 * <p>11 dígitos com dois dígitos verificadores (DV) calculados módulo 11. A entrada aceita máscara
 * ({@code 123.456.789-09}) ou apenas dígitos; o valor armazenado e serializado é sempre sem
 * máscara.
 *
 * <p>{@code 00000000000} é aceito propositalmente como sentinela "consumidor final" (RN-CAD-001 em
 * {@code docs/domain/11-cadastros.md}). Sequências de dígitos repetidos não-zero ({@code
 * 11111111111}..{@code 99999999999}) são rejeitadas para evitar cadastros falsos triviais.
 */
public record Cpf(@JsonValue String value) {

  public Cpf {
    Objects.requireNonNull(value, "value");
    String digits = value.replaceAll("\\D", "");
    if (digits.length() != 11) {
      throw new InvalidCpfException(value, "CPF deve conter 11 dígitos.");
    }
    if (isRepeatedNonZero(digits)) {
      throw new InvalidCpfException(value, "CPF com dígitos repetidos inválido.");
    }
    if (!isValidDv(digits)) {
      throw new InvalidCpfException(value, "Dígitos verificadores do CPF inválidos.");
    }
    value = digits;
  }

  @JsonCreator
  public static Cpf of(String raw) {
    return new Cpf(raw);
  }

  /**
   * Versão formatada para apresentação: {@code 123.456.789-09}. Persistir sempre {@link #value}.
   */
  public String formatted() {
    return value.substring(0, 3)
        + "."
        + value.substring(3, 6)
        + "."
        + value.substring(6, 9)
        + "-"
        + value.substring(9);
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
    int dv1 = calculateDv(digits, 10);
    if (Character.getNumericValue(digits.charAt(9)) != dv1) {
      return false;
    }
    int dv2 = calculateDv(digits, 11);
    return Character.getNumericValue(digits.charAt(10)) == dv2;
  }

  private static int calculateDv(String digits, int startWeight) {
    int sum = 0;
    for (int i = 0; i < startWeight - 1; i++) {
      sum += Character.getNumericValue(digits.charAt(i)) * (startWeight - i);
    }
    int mod = sum % 11;
    return mod < 2 ? 0 : 11 - mod;
  }
}
