package dev.dimi.erp.shared.internal.multitenancy;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;
import org.hibernate.engine.jdbc.connections.spi.MultiTenantConnectionProvider;

/**
 * Hibernate {@link MultiTenantConnectionProvider} que entrega connections com {@code search_path}
 * setado para o schema do tenant ativo.
 *
 * <p>O search_path é resetado para {@code shared} no release para evitar leak entre conexões
 * reusadas do pool. A validação do nome do schema garante que strings não confiáveis nunca cheguem
 * aqui (extra defesa contra SQL injection via tenant identifier).
 */
class SchemaPerTenantConnectionProvider implements MultiTenantConnectionProvider<String> {

  private static final String DEFAULT_SCHEMA = "shared";

  private final DataSource dataSource;

  SchemaPerTenantConnectionProvider(DataSource dataSource) {
    this.dataSource = dataSource;
  }

  @Override
  public Connection getAnyConnection() throws SQLException {
    Connection c = dataSource.getConnection();
    setSearchPath(c, DEFAULT_SCHEMA);
    return c;
  }

  @Override
  public void releaseAnyConnection(Connection connection) throws SQLException {
    try {
      setSearchPath(connection, DEFAULT_SCHEMA);
    } finally {
      connection.close();
    }
  }

  @Override
  public Connection getConnection(String tenantIdentifier) throws SQLException {
    Connection c = dataSource.getConnection();
    if (DEFAULT_SCHEMA.equals(tenantIdentifier)) {
      setSearchPath(c, DEFAULT_SCHEMA);
    } else {
      setSearchPath(c, quoteIdentifier(tenantIdentifier) + ", " + DEFAULT_SCHEMA);
    }
    return c;
  }

  @Override
  public void releaseConnection(String tenantIdentifier, Connection connection)
      throws SQLException {
    try {
      setSearchPath(connection, DEFAULT_SCHEMA);
    } finally {
      connection.close();
    }
  }

  @Override
  public boolean supportsAggressiveRelease() {
    return false;
  }

  @Override
  public boolean isUnwrappableAs(Class<?> unwrapType) {
    return MultiTenantConnectionProvider.class.equals(unwrapType)
        || SchemaPerTenantConnectionProvider.class.equals(unwrapType);
  }

  @Override
  @SuppressWarnings("unchecked")
  public <T> T unwrap(Class<T> unwrapType) {
    if (isUnwrappableAs(unwrapType)) {
      return (T) this;
    }
    throw new IllegalArgumentException("Cannot unwrap to " + unwrapType.getName());
  }

  private static void setSearchPath(Connection c, String searchPath) throws SQLException {
    try (var st = c.createStatement()) {
      st.execute("SET search_path TO " + searchPath);
    }
  }

  /**
   * Aceita apenas identificadores no formato {@code tenant_<lowercase_with_underscores>}. Qualquer
   * desvio rejeita por princípio de defesa em profundidade — embora {@code TenantContext} já
   * imponha esse padrão no record {@code Tenant}, validamos novamente aqui porque o resolver pode
   * vir de outras fontes em testes.
   */
  private static String quoteIdentifier(String schemaName) {
    if (schemaName == null || !schemaName.matches("^tenant_[a-z0-9_]+$")) {
      throw new IllegalArgumentException("schema name inválido: " + schemaName);
    }
    return "\"" + schemaName + "\"";
  }
}
