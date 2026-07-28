package dev.dimi.erp.shared.internal.multitenancy;

import javax.sql.DataSource;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.context.spi.CurrentTenantIdentifierResolver;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;
import org.springframework.boot.autoconfigure.orm.jpa.HibernatePropertiesCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Registra os providers Hibernate que implementam schema-per-tenant.
 *
 * <p>Doc: {@code docs/architecture/03-multi-tenancy.md}.
 */
@Configuration(proxyBeanMethods = false)
class MultiTenancyConfig {

  @Bean
  MultiTenantConnectionProvider<String> multiTenantConnectionProvider(DataSource dataSource) {
    return new SchemaPerTenantConnectionProvider(dataSource);
  }

  @Bean
  CurrentTenantIdentifierResolver<String> currentTenantIdentifierResolver() {
    return new CurrentTenantResolver();
  }

  @Bean
  HibernatePropertiesCustomizer multiTenancyHibernateCustomizer(
      MultiTenantConnectionProvider<String> connectionProvider,
      CurrentTenantIdentifierResolver<String> tenantResolver) {
    return props -> {
      props.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
      props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
    };
  }
}
