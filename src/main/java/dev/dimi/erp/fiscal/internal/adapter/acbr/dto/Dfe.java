package dev.dimi.erp.fiscal.internal.adapter.acbr.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Representação do documento fiscal eletrônico (NFe modelo 55 no v1) tal como retornado pelo SaaS
 * oficial {@code acbr.api.br} em {@code POST /nfe}, {@code GET /nfe/{id}} e {@code POST
 * /nfe/{id}/cancelamento}.
 *
 * <p>{@link #status} segue o ciclo {@code pendente → autorizado | rejeitado | cancelado}; {@link
 * #autorizacao} só é preenchida quando autorizado; {@link #motivo} aparece em rejeição ou
 * cancelamento.
 *
 * <p>{@code @JsonIgnoreProperties(ignoreUnknown = true)} para sobreviver a campos extras que o
 * upstream possa adicionar (ex.: links HATEOAS, metadados de conta) sem quebrar o adapter.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record Dfe(
    String id,
    String ambiente,
    @JsonProperty("created_at") OffsetDateTime createdAt,
    String status,
    String referencia,
    @JsonProperty("data_emissao") OffsetDateTime dataEmissao,
    String modelo,
    String serie,
    String numero,
    @JsonProperty("tipo_emissao") String tipoEmissao,
    @JsonProperty("valor_total") BigDecimal valorTotal,
    String chave,
    Autorizacao autorizacao,
    Motivo motivo) {

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Autorizacao(
      String protocolo, @JsonProperty("data_autorizacao") OffsetDateTime dataAutorizacao) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Motivo(String codigo, String descricao) {}
}
