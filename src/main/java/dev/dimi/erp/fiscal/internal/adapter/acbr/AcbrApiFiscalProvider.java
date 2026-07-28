package dev.dimi.erp.fiscal.internal.adapter.acbr;

import dev.dimi.erp.fiscal.AuthorizationResult;
import dev.dimi.erp.fiscal.FiscalProvider;
import dev.dimi.erp.fiscal.FiscalProviderException;
import dev.dimi.erp.fiscal.InvoiceDraft;
import dev.dimi.erp.fiscal.internal.adapter.acbr.dto.Dfe;
import dev.dimi.erp.fiscal.internal.adapter.acbr.dto.NfePedidoEmissao;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * Adapter HTTP de {@link FiscalProvider} contra o SaaS oficial {@code acbr.api.br}. Slice 8.3b: só
 * implementa {@link #authorize(InvoiceDraft)} (emissão NFe modelo 55). Cancelamento/CCe entram no
 * 8.6.
 *
 * <p>Pipeline: {@link NfeRequestBuilder} monta o payload a partir do {@link InvoiceDraft}; {@link
 * AcbrApiClient} faz {@code POST /nfe} já injetando OAuth2 e tratando 401; o {@link Dfe} retornado
 * vai para {@link #mapResponse(Dfe)} que traduz para nosso {@link AuthorizationResult} (sealed).
 *
 * <p>Falhas técnicas (timeout, 5xx upstream, payload inválido) chegam como {@link AcbrApiException}
 * e são reembrulhadas em {@link FiscalProviderException} — o caller decide propagar 502 ou cair em
 * contingência (8.6). Status semânticos {@code PROCESSANDO} também viram {@link
 * FiscalProviderException}: o v1 não suporta polling assíncrono (entra em 8.6).
 *
 * <p>Ativado apenas quando {@code erp-poc.fiscal.provider=acbr-api}. Marcado {@code @Primary} para
 * sobrepor qualquer outro {@link FiscalProvider} eventualmente registrado no mesmo contexto.
 */
@Component
@Primary
@ConditionalOnProperty(prefix = "erp-poc.fiscal", name = "provider", havingValue = "acbr-api")
public class AcbrApiFiscalProvider implements FiscalProvider {

  private static final Logger log = LoggerFactory.getLogger(AcbrApiFiscalProvider.class);

  private final NfeRequestBuilder builder;
  private final AcbrApiClient client;

  public AcbrApiFiscalProvider(NfeRequestBuilder builder, AcbrApiClient client) {
    this.builder = builder;
    this.client = client;
  }

  @Override
  public AuthorizationResult authorize(InvoiceDraft draft) {
    NfePedidoEmissao body = builder.build(draft);
    log.info(
        "ACBr emit invoiceId={} ambiente={} ref={}",
        draft.invoiceId(),
        body.ambiente(),
        body.referencia());
    Dfe response;
    try {
      response = client.emitirNfe(body);
    } catch (AcbrApiException e) {
      log.warn("ACBr upstream code={} status={}", e.acbrCode(), e.upstreamStatus());
      throw new FiscalProviderException(
          "ACBr upstream: " + e.acbrCode() + " — " + e.getMessage(), e);
    }
    return mapResponse(response);
  }

  private AuthorizationResult mapResponse(Dfe dfe) {
    String status = dfe.status() == null ? "" : dfe.status().toUpperCase();
    return switch (status) {
      case "AUTORIZADO", "AUTHORIZED" ->
          new AuthorizationResult.Authorized(
              dfe.chave(),
              dfe.autorizacao() != null ? dfe.autorizacao().protocolo() : null,
              dfe.autorizacao() != null && dfe.autorizacao().dataAutorizacao() != null
                  ? dfe.autorizacao().dataAutorizacao().toInstant()
                  : Instant.now());
      case "REJEITADO", "REJECTED", "DENEGADO" ->
          new AuthorizationResult.Rejected(
              dfe.motivo() != null ? dfe.motivo().codigo() : "unknown",
              dfe.motivo() != null ? dfe.motivo().descricao() : "Rejeitado sem motivo");
      case "PROCESSANDO", "PROCESSING" ->
          throw new FiscalProviderException(
              "ACBr respondeu PROCESSANDO — adapter v1 não suporta polling assíncrono (8.6).");
      default -> throw new FiscalProviderException("Status ACBr desconhecido: " + dfe.status());
    };
  }
}
