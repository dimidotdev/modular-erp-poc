package dev.dimi.erp.fiscal.internal.adapter.acbr;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Falha não-recuperável do SaaS oficial {@code acbr.api.br} após o adapter já ter retentado uma vez
 * (no caso de 401). Carrega o {@code upstreamStatus} HTTP original e o {@code acbrCode} do envelope
 * {@code {"error":{"code","message"}}} para que o cliente da API consiga distinguir erro de
 * validação (status SEFAZ), erro de infraestrutura ou erro de autorização.
 *
 * <p>Mapeada para HTTP 502 (Bad Gateway) — do ponto de vista do cliente do Modular ERP POC,
 * qualquer falha do upstream fiscal é "gateway upstream falhou". A causa específica vive em {@code
 * properties()} do {@link ProblemDetail}.
 */
public class AcbrApiException extends DomainException {

  private final int upstreamStatus;
  private final String acbrCode;

  public AcbrApiException(int upstreamStatus, String acbrCode, String message) {
    super(
        "fiscal.acbr-upstream-error",
        HttpStatus.BAD_GATEWAY,
        "Falha no provedor fiscal ACBr",
        "ACBr API respondeu status="
            + upstreamStatus
            + " code="
            + acbrCode
            + " message="
            + message);
    this.upstreamStatus = upstreamStatus;
    this.acbrCode = acbrCode;
  }

  public int upstreamStatus() {
    return upstreamStatus;
  }

  public String acbrCode() {
    return acbrCode;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("upstreamStatus", upstreamStatus, "acbrCode", acbrCode);
  }
}
