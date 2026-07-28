package dev.dimi.erp.shared.multitenancy;

import java.util.Objects;
import java.util.Optional;

/**
 * Contexto de tenant da operação corrente, armazenado em {@link ThreadLocal}.
 *
 * <p>Setado pelo filter de autenticação (ou de bootstrap para operações da plataforma) no início da
 * requisição e limpo no {@code finally}. O Hibernate {@code CurrentTenantIdentifierResolver}
 * consulta este contexto para resolver o schema.
 *
 * <p>Spring Boot 3.5 com virtual threads habilitado (config padrão deste projeto) propaga {@code
 * ThreadLocal} normalmente dentro da virtual thread da requisição. Para tasks {@code @Async}
 * registramos {@code TaskDecorator} apropriado quando necessário (futuro).
 */
public final class TenantContext {

  private static final ThreadLocal<Tenant> CURRENT = new ThreadLocal<>();

  private TenantContext() {
    // utility
  }

  /** Seta o tenant da operação corrente. */
  public static void set(Tenant tenant) {
    CURRENT.set(Objects.requireNonNull(tenant, "tenant não pode ser nulo"));
  }

  /** Limpa o tenant da operação corrente. Deve ser chamado no {@code finally} do filter. */
  public static void clear() {
    CURRENT.remove();
  }

  /** Retorna o tenant atual, ou vazio se nenhuma operação tenant-bound estiver em curso. */
  public static Optional<Tenant> current() {
    return Optional.ofNullable(CURRENT.get());
  }

  /**
   * Retorna o tenant atual ou falha. Use em código que <em>sabe</em> que deveria haver tenant — uma
   * chamada cair aqui sem tenant indica bug de configuração de filter/security.
   */
  public static Tenant currentOrThrow() {
    Tenant t = CURRENT.get();
    if (t == null) {
      throw new IllegalStateException(
          "Nenhum tenant no contexto. Operação tenant-bound chamada fora de filter autenticado?");
    }
    return t;
  }
}
