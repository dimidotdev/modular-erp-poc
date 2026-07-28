package dev.dimi.erp.identidade.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class PasswordPolicyValidatorTest {

  private final PasswordPolicyValidator validator = new PasswordPolicyValidator();
  private static final String USERNAME = "joao.silva";
  private static final String EMAIL = "joao.silva@example.test";
  private static final String FULL_NAME = "João Silva Pereira";

  @Nested
  @DisplayName("validate(senha-valida)")
  class Valid {

    @Test
    void senhaForte_naoDeveLancar() {
      assertThatNoException()
          .isThrownBy(
              () -> validator.validate("Tr0ub4dor&pivot-eclipse", USERNAME, EMAIL, FULL_NAME));
    }

    @Test
    void exatamenteDozeCaracteresSemDadosPessoais_deveAceitar() {
      assertThatNoException()
          .isThrownBy(() -> validator.validate("xK9!mQ-vL2Pz", USERNAME, EMAIL, FULL_NAME));
    }
  }

  @Nested
  @DisplayName("validate(senha-curta)")
  class TooShort {

    @Test
    void senhaCom11Caracteres_deveLancarComMotivoTooShort() {
      assertThatThrownBy(() -> validator.validate("Curta12345!", USERNAME, EMAIL, FULL_NAME))
          .isInstanceOf(WeakPasswordException.class)
          .hasFieldOrPropertyWithValue("code", "identidade.weak-password");
    }

    @Test
    void senhaVazia_deveLancarApenasComTooShort() {
      WeakPasswordException ex =
          (WeakPasswordException)
              org.junit.jupiter.api.Assertions.assertThrows(
                  WeakPasswordException.class,
                  () -> validator.validate("", USERNAME, EMAIL, FULL_NAME));
      assertThat(violations(ex))
          .anyMatch(v -> v.startsWith("password.too-short"))
          .noneMatch(v -> v.startsWith("password.contains-"));
    }
  }

  @Nested
  @DisplayName("validate(senha-com-dados-pessoais)")
  class ContainsPersonalData {

    @Test
    void senhaContendoUsername_deveListarContainsUsername() {
      WeakPasswordException ex =
          assertThrowsPolicy("Joao.Silva-vault!", USERNAME, EMAIL, FULL_NAME);
      assertThat(violations(ex)).anyMatch(v -> v.startsWith("password.contains-username"));
    }

    @Test
    void senhaContendoParteDoEmail_deveListarContainsEmail() {
      WeakPasswordException ex =
          assertThrowsPolicy("X1!_joao.silva_Y2#", USERNAME, EMAIL, FULL_NAME);
      // Não checa específico pois username e email-local são iguais aqui (joao.silva);
      // o validator detecta como contains-username primeiro ou ambos.
      assertThat(violations(ex))
          .anyMatch(
              v ->
                  v.startsWith("password.contains-username")
                      || v.startsWith("password.contains-email"));
    }

    @Test
    void senhaContendoParteDoNome_deveListarContainsName() {
      WeakPasswordException ex =
          assertThrowsPolicy(
              "aleatoria-pereira-x9!", "user42", "user42@example.invalid", FULL_NAME);
      assertThat(violations(ex)).anyMatch(v -> v.startsWith("password.contains-name"));
    }

    @Test
    void tokenCurto_naoDispara() {
      // "Joa" tem 3 chars, menor que MIN_TOKEN_LENGTH=4, então não deve disparar.
      assertThatNoException()
          .isThrownBy(
              () ->
                  validator.validate("Joa-rT9!_xKzVm", "Joa", "joa@example.invalid", "Joa Silva"));
    }
  }

  // -----------------------------------------------------------------------------
  // helpers
  // -----------------------------------------------------------------------------

  private WeakPasswordException assertThrowsPolicy(
      String password, String username, String email, String fullName) {
    return (WeakPasswordException)
        org.junit.jupiter.api.Assertions.assertThrows(
            WeakPasswordException.class,
            () -> validator.validate(password, username, email, fullName));
  }

  @SuppressWarnings("unchecked")
  private static java.util.List<String> violations(WeakPasswordException ex) {
    return (java.util.List<String>) ex.properties().get("violations");
  }
}
