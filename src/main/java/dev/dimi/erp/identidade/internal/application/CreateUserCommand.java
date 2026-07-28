package dev.dimi.erp.identidade.internal.application;

import dev.dimi.erp.identidade.UserId;
import java.util.List;

/**
 * Comando para criação administrativa de um usuário num tenant.
 *
 * @param username login do usuário (case-insensitive, unique no tenant)
 * @param email email do usuário (case-insensitive, unique no tenant)
 * @param fullName nome completo (usado na política de senha para evitar contains-name)
 * @param password senha em claro; será validada e hashed com Argon2id antes de persistir
 * @param roleCodes códigos das roles a atribuir (ex: {@code OWNER}, {@code VENDEDOR}); deve conter
 *     pelo menos uma role
 * @param mustChangePassword se verdadeiro, força troca no primeiro login
 * @param createdBy id do usuário autenticado que criou este registro (audit)
 */
public record CreateUserCommand(
    String username,
    String email,
    String fullName,
    String password,
    List<String> roleCodes,
    boolean mustChangePassword,
    UserId createdBy) {

  public CreateUserCommand {
    if (username == null || username.isBlank())
      throw new IllegalArgumentException("username obrigatório");
    if (email == null || email.isBlank()) throw new IllegalArgumentException("email obrigatório");
    if (fullName == null || fullName.isBlank())
      throw new IllegalArgumentException("fullName obrigatório");
    if (password == null) throw new IllegalArgumentException("password obrigatório");
    if (roleCodes == null || roleCodes.isEmpty())
      throw new IllegalArgumentException("usuário precisa de pelo menos uma role");
    roleCodes = List.copyOf(roleCodes);
  }
}
