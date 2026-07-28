package dev.dimi.erp.shared.multitenancy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Registra o {@link TenantPropagatingTaskDecorator} no executor padrão do {@code @Async} (Spring
 * Boot 3.5 com virtual threads usa {@code SimpleAsyncTaskExecutor} via auto-config; este customizer
 * aplica o decorator sem desligar virtual threads).
 *
 * <p>{@code @EnableAsync} ativa o suporte se ainda não estiver — idempotente quando algum outro
 * módulo já tiver feito.
 */
@Configuration
@EnableAsync
public class MultitenancyAsyncConfig {

  @Bean
  public TenantPropagatingTaskDecorator tenantPropagatingTaskDecorator() {
    return new TenantPropagatingTaskDecorator();
  }

  /**
   * Customiza o builder do executor async padrão para registrar o decorator. Spring Boot detecta
   * automaticamente este customizer e o aplica ao bean {@code applicationTaskExecutor}.
   */
  @Bean
  public org.springframework.boot.task.SimpleAsyncTaskExecutorCustomizer
      tenantPropagatingCustomizer(TenantPropagatingTaskDecorator decorator) {
    return builder -> builder.setTaskDecorator(decorator);
  }
}
