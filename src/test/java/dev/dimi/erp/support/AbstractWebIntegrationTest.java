package dev.dimi.erp.support;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Base para ITs que exercitam endpoints HTTP via {@link MockMvc}. Estende {@link
 * AbstractIntegrationTest} (Testcontainers + Postgres real) e ativa o MVC mock auto-configurado.
 */
@AutoConfigureMockMvc
public abstract class AbstractWebIntegrationTest extends AbstractIntegrationTest {

  @Autowired protected MockMvc mockMvc;
  @Autowired protected ObjectMapper objectMapper;

  /**
   * 14 dígitos aleatórios para satisfazer {@code @Pattern(^[0-9]{14}$)} e a unique constraint em
   * {@code shared.tenant.cnpj}. Não passa por DV oficial — o provisioning não valida ainda.
   */
  protected static String randomCnpj() {
    StringBuilder sb = new StringBuilder(14);
    for (int i = 0; i < 14; i++) {
      sb.append(ThreadLocalRandom.current().nextInt(10));
    }
    return sb.toString();
  }
}
