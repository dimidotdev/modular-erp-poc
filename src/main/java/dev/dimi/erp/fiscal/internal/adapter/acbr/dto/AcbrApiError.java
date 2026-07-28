package dev.dimi.erp.fiscal.internal.adapter.acbr.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Envelope de erro retornado pelo SaaS oficial {@code acbr.api.br} quando uma chamada falha (4xx ou
 * 5xx). Formato canônico documentado no contrato:
 *
 * <pre>{@code
 * {"error": {"code": "validation_failed", "message": "..."}}
 * }</pre>
 *
 * <p>Quando o body não conforma com este envelope (ex.: erro 502 do reverse proxy upstream com
 * HTML), o parser falha e o adapter cai no fallback de mensagem crua — ver {@code
 * AcbrApiClient#onError}.
 */
public record AcbrApiError(@JsonProperty("error") Detail error) {

  public record Detail(String code, String message) {}
}
