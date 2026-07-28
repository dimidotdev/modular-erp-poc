package dev.dimi.erp.fiscal.internal.adapter.acbr;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuração do adapter {@code AcbrApiFiscalProvider} contra o SaaS oficial {@code acbr.api.br}.
 * Bind de {@code erp-poc.fiscal.acbr-api.*} em {@code application.yml} (e overrides via env vars).
 *
 * <ul>
 *   <li>{@code baseUrl}: URL base da API (ex: {@code https://acbr.api.br}).
 *   <li>{@code auth}: parâmetros OAuth2 client_credentials (Keycloak).
 *   <li>{@code timeouts}: timeouts de conexão e leitura em milissegundos.
 * </ul>
 *
 * <p>{@code clientId}/{@code clientSecret} nunca devem ser commitados — sempre via env var em prod
 * (Azure Key Vault) ou via {@code application-local.yml} gitignorado em dev.
 */
@ConfigurationProperties(prefix = "erp-poc.fiscal.acbr-api")
public record AcbrApiProperties(String baseUrl, Auth auth, Timeouts timeouts) {

  public record Auth(String tokenUrl, String clientId, String clientSecret) {}

  public record Timeouts(int connectMs, int readMs) {}
}
