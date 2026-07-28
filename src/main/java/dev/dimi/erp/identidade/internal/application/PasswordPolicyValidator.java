package dev.dimi.erp.identidade.internal.application;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * Política de senha do v1, conforme {@code docs/architecture/02-seguranca.md § Hash de senhas}.
 *
 * <ul>
 *   <li>Mínimo 12 caracteres.
 *   <li>Não pode conter o {@code username}, parte local do {@code email} ou pedaço do {@code
 *       fullName} (case-insensitive, 4+ caracteres).
 *   <li>Sem requisitos arbitrários de caracteres especiais (NIST SP 800-63B: comprimento +
 *       diversidade natural já basta).
 * </ul>
 *
 * <p>Pendente para v1.1: validação contra top 10k senhas comprometidas (haveibeenpwned k-anonymity
 * API ou dataset offline).
 */
@Component
public class PasswordPolicyValidator {

  static final int MIN_LENGTH = 12;
  static final int MIN_TOKEN_LENGTH = 4;

  /**
   * Valida a senha. Lança {@link WeakPasswordException} se violar a política, listando todas as
   * violações de uma vez (UX).
   */
  public void validate(String password, String username, String email, String fullName) {
    List<String> violations = collectViolations(password, username, email, fullName);
    if (!violations.isEmpty()) {
      throw new WeakPasswordException(violations);
    }
  }

  List<String> collectViolations(String password, String username, String email, String fullName) {
    List<String> violations = new ArrayList<>();
    if (password == null || password.length() < MIN_LENGTH) {
      violations.add("password.too-short: mínimo " + MIN_LENGTH + " caracteres");
      // Demais checks não fazem sentido se password é nula/curta.
      if (password == null || password.isEmpty()) {
        return violations;
      }
    }
    String lower = password.toLowerCase();
    if (containsToken(lower, username)) {
      violations.add("password.contains-username: senha não pode conter o username");
    }
    String emailLocal = localPart(email);
    if (containsToken(lower, emailLocal)) {
      violations.add("password.contains-email: senha não pode conter parte do email");
    }
    if (fullName != null) {
      for (String part : fullName.toLowerCase().split("\\s+")) {
        if (containsToken(lower, part)) {
          violations.add("password.contains-name: senha não pode conter parte do nome completo");
          break;
        }
      }
    }
    return violations;
  }

  private static boolean containsToken(String passwordLower, String token) {
    if (token == null) return false;
    String t = token.toLowerCase().trim();
    return t.length() >= MIN_TOKEN_LENGTH && passwordLower.contains(t);
  }

  private static String localPart(String email) {
    if (email == null) return null;
    int at = email.indexOf('@');
    return at > 0 ? email.substring(0, at) : email;
  }
}
