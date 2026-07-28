package dev.dimi.erp.identidade.internal.domain;

/**
 * Status de um {@link UserAccount}. Persistido como TEXT no Postgres via
 * {@code @Enumerated(STRING)}.
 *
 * <ul>
 *   <li>{@code ACTIVE}: pode fazer login.
 *   <li>{@code SUSPENDED}: bloqueio temporário (lockout, suspeita); reversível.
 *   <li>{@code DEACTIVATED}: desligado (ex: funcionário fora da empresa); efetivamente final.
 * </ul>
 */
public enum UserStatus {
  ACTIVE,
  SUSPENDED,
  DEACTIVATED
}
