package dev.dimi.erp.shared.multitenancy;

import org.springframework.core.task.TaskDecorator;

/**
 * {@link TaskDecorator} que captura o {@link TenantContext} da thread caller no momento do submit e
 * o re-aplica na thread executora antes de rodar a task, limpando ao fim.
 *
 * <p>Necessário porque {@code TenantContext} é {@link ThreadLocal} — Spring {@code @Async}
 * (incluindo {@code @ApplicationModuleListener} do Modulith) executa em threads separadas do
 * caller, perdendo o ThreadLocal. Sem essa propagação, listeners async que operam em entidades
 * tenant-bound (Hibernate {@code CurrentTenantIdentifierResolver}) caem em {@code
 * IllegalStateException} ou rotam ao schema errado.
 *
 * <p>Caso o caller não tenha tenant no contexto (ex: tasks de plataforma), a task roda sem contexto
 * também — sem efeito colateral.
 */
public class TenantPropagatingTaskDecorator implements TaskDecorator {

  @Override
  public Runnable decorate(Runnable runnable) {
    Tenant captured = TenantContext.current().orElse(null);
    return () -> {
      if (captured != null) {
        TenantContext.set(captured);
      }
      try {
        runnable.run();
      } finally {
        if (captured != null) {
          TenantContext.clear();
        }
      }
    };
  }
}
