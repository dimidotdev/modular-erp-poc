package dev.dimi.erp.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Base para testes de integração que precisam de app + banco real.
 *
 * <p>Sobe um Postgres 16 via Testcontainers como <em>singleton da JVM</em> — start uma única vez no
 * primeiro acesso e nunca para (JVM exit fecha). Sem {@code @Container}/{@code @Testcontainers}
 * para evitar que o JUnit extension destrua o container entre classes IT (cada classe nova teria
 * que recriar e mudar a porta, invalidando o pool Hikari do Spring context cacheado).
 *
 * <p>Anotado com {@link EnabledIfDockerAvailable}: máquinas sem permissão no socket do Docker pulam
 * os testes em vez de quebrar o build. CI tem Docker, então a suite roda completa lá.
 */
@SpringBootTest
@ActiveProfiles("test")
@EnabledIfDockerAvailable
public abstract class AbstractIntegrationTest {

  @SuppressWarnings("resource")
  protected static final PostgreSQLContainer<?> POSTGRES =
      new PostgreSQLContainer<>("postgres:16-alpine")
          .withDatabaseName("modular_erp_poc_test")
          .withUsername("modular_erp_poc")
          .withPassword("modular_erp_poc")
          .withReuse(true);

  static {
    POSTGRES.start();
  }

  @DynamicPropertySource
  static void registerDataSourceProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
    registry.add("spring.datasource.username", POSTGRES::getUsername);
    registry.add("spring.datasource.password", POSTGRES::getPassword);
  }
}
