package dev.dimi.erp.fiscal.internal.adapter.acbr;

import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dimi.erp.fiscal.internal.adapter.acbr.auth.AcbrTokenIssuer;
import dev.dimi.erp.fiscal.internal.adapter.acbr.dto.AcbrApiError;
import dev.dimi.erp.fiscal.internal.adapter.acbr.dto.Dfe;
import dev.dimi.erp.fiscal.internal.adapter.acbr.dto.NfePedidoCancelamento;
import dev.dimi.erp.fiscal.internal.adapter.acbr.dto.NfePedidoEmissao;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP de baixo nível contra o SaaS oficial {@code acbr.api.br}. Encapsula:
 *
 * <ul>
 *   <li>Injeção do header {@code Authorization: Bearer <token>} obtido do {@link AcbrTokenIssuer} a
 *       cada chamada.
 *   <li>Parse do envelope de erro {@code {"error":{"code","message"}}} em {@link AcbrApiException}
 *       (com fallback para body cru se o JSON for inválido).
 *   <li>Retentativa única em 401 — invalida cache de token e refaz a chamada uma vez antes de
 *       propagar como {@link AcbrApiException} normal.
 * </ul>
 *
 * <p>Endpoints cobertos no v1 (slice 8.3b): emissão, consulta e cancelamento de NFe modelo 55.
 * Demais operações (CCe, inutilização, DFe, manifestação) entram nos slices 8.5/8.6.
 *
 * <p>Ativado apenas quando {@code erp-poc.fiscal.provider=acbr-api}. Em dev/test o {@code
 * InMemoryFiscalProvider} é o default e este bean não sobe.
 */
@Component
@ConditionalOnProperty(prefix = "erp-poc.fiscal", name = "provider", havingValue = "acbr-api")
public class AcbrApiClient {

  private static final Logger log = LoggerFactory.getLogger(AcbrApiClient.class);

  private final AcbrTokenIssuer tokenIssuer;
  private final RestClient http;
  private final ObjectMapper objectMapper;

  public AcbrApiClient(
      AcbrApiProperties props,
      AcbrTokenIssuer tokenIssuer,
      RestClient.Builder builder,
      ObjectMapper objectMapper) {
    this.tokenIssuer = tokenIssuer;
    this.objectMapper = objectMapper;
    this.http =
        builder
            .baseUrl(props.baseUrl())
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
  }

  /** Emite uma NFe modelo 55 contra a SEFAZ via SaaS. */
  public Dfe emitirNfe(NfePedidoEmissao body) {
    return execute(
        () ->
            http.post()
                .uri("/nfe")
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::onError)
                .body(Dfe.class));
  }

  /** Consulta o estado atual de um documento previamente emitido pelo {@code id} do SaaS. */
  public Dfe consultarNfe(String id) {
    return execute(
        () ->
            http.get()
                .uri("/nfe/{id}", id)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::onError)
                .body(Dfe.class));
  }

  /** Solicita cancelamento de uma NFe autorizada. Janela SEFAZ: 24h após autorização. */
  public Dfe cancelarNfe(String id, String justificativa) {
    return execute(
        () ->
            http.post()
                .uri("/nfe/{id}/cancelamento", id)
                .header(HttpHeaders.AUTHORIZATION, bearer())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new NfePedidoCancelamento(justificativa))
                .retrieve()
                .onStatus(s -> !s.is2xxSuccessful(), this::onError)
                .body(Dfe.class));
  }

  private String bearer() {
    return "Bearer " + tokenIssuer.currentToken();
  }

  /**
   * Wrapper de retentativa: se a chamada falhar com {@link AcbrUnauthorizedException} (401),
   * invalida o cache de token e tenta uma única vez novamente. Se ainda assim falhar, propaga a
   * exceção. Outras falhas ({@link AcbrApiException}) propagam imediatamente.
   */
  private <T> T execute(Supplier<T> call) {
    try {
      return call.get();
    } catch (AcbrUnauthorizedException retry) {
      log.warn("ACBr respondeu 401 — invalidando cache de token e retentando uma vez");
      tokenIssuer.invalidate();
      return call.get();
    }
  }

  private void onError(HttpRequest request, ClientHttpResponse resp) throws IOException {
    int status = resp.getStatusCode().value();
    String body = new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8);
    String code = "unknown";
    String message = body;
    try {
      AcbrApiError err = objectMapper.readValue(body, AcbrApiError.class);
      if (err.error() != null) {
        code = err.error().code();
        message = err.error().message();
      }
    } catch (Exception ignore) {
      // body não é JSON conforme com o envelope — fallback para body cru
    }
    log.warn("ACBr error status={} code={} message={}", status, code, message);
    if (status == 401) {
      throw new AcbrUnauthorizedException(code, message);
    }
    throw new AcbrApiException(status, code, message);
  }

  /**
   * Sinal interno de "token expirado/revogado no upstream". Não é {@link AcbrApiException} porque
   * queremos retentar antes de desistir — só vira {@link AcbrApiException} se a segunda tentativa
   * também falhar.
   */
  static class AcbrUnauthorizedException extends RuntimeException {
    AcbrUnauthorizedException(String code, String message) {
      super(code + ": " + message);
    }
  }
}
