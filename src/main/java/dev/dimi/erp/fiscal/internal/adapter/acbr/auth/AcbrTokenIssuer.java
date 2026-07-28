package dev.dimi.erp.fiscal.internal.adapter.acbr.auth;

import com.fasterxml.jackson.annotation.JsonProperty;
import dev.dimi.erp.fiscal.internal.adapter.acbr.AcbrApiProperties;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Emite e cacheia o access token OAuth2 (client_credentials) usado pelo {@code
 * AcbrApiFiscalProvider} para falar com o SaaS oficial {@code acbr.api.br}.
 *
 * <p>Cache in-memory via {@link AtomicReference} + {@code synchronized} no caminho de obtenção do
 * token: previne thundering herd em chamadas concorrentes (uma só request OAuth2 por refresh).
 * Renovação proativa com buffer de {@value #EXPIRY_BUFFER_SECONDS}s evita 401 em chamadas que caem
 * na janela final de expiração.
 *
 * <p>Ativado apenas quando {@code erp-poc.fiscal.provider=acbr-api}. Em dev/test o {@code
 * InMemoryFiscalProvider} é o default e este bean não sobe.
 */
@Component
@ConditionalOnProperty(prefix = "erp-poc.fiscal", name = "provider", havingValue = "acbr-api")
public class AcbrTokenIssuer {

  private static final Logger log = LoggerFactory.getLogger(AcbrTokenIssuer.class);
  private static final long EXPIRY_BUFFER_SECONDS = 60;

  private final AcbrApiProperties properties;
  private final RestClient authClient;
  private final AtomicReference<AcbrAccessToken> cached = new AtomicReference<>();

  public AcbrTokenIssuer(AcbrApiProperties properties, RestClient.Builder builder) {
    this.properties = properties;
    this.authClient = builder.build();
  }

  /**
   * Retorna o token corrente, refrescando contra o Keycloak se ausente ou expirado (com buffer).
   * Synchronized garante uma única requisição OAuth2 em concorrência.
   */
  public synchronized String currentToken() {
    AcbrAccessToken token = cached.get();
    if (token != null && !token.isExpired(Instant.now(), EXPIRY_BUFFER_SECONDS)) {
      return token.value();
    }
    AcbrAccessToken fresh = fetch();
    cached.set(fresh);
    log.info("ACBr token refreshed expires_at={}", fresh.expiresAt());
    return fresh.value();
  }

  /**
   * Força a invalidação do cache. Usado pelo adapter quando a API retorna 401 mesmo com token
   * supostamente válido (clock drift, revogação manual no Keycloak).
   */
  public void invalidate() {
    cached.set(null);
  }

  private AcbrAccessToken fetch() {
    var form = new LinkedMultiValueMap<String, String>();
    form.add("grant_type", "client_credentials");
    form.add("client_id", properties.auth().clientId());
    form.add("client_secret", properties.auth().clientSecret());
    try {
      var resp =
          authClient
              .post()
              .uri(properties.auth().tokenUrl())
              .contentType(MediaType.APPLICATION_FORM_URLENCODED)
              .accept(MediaType.APPLICATION_JSON)
              .body(form)
              .retrieve()
              .body(TokenResponse.class);
      if (resp == null || resp.accessToken() == null) {
        throw new AcbrAuthenticationException("Token vazio");
      }
      return new AcbrAccessToken(
          resp.accessToken(),
          Instant.now().plusSeconds(resp.expiresIn() == null ? 60 : resp.expiresIn()));
    } catch (RestClientException e) {
      throw new AcbrAuthenticationException("Falha OAuth2: " + e.getMessage(), e);
    }
  }

  record TokenResponse(
      @JsonProperty("access_token") String accessToken,
      @JsonProperty("expires_in") Integer expiresIn,
      @JsonProperty("token_type") String tokenType) {}
}
