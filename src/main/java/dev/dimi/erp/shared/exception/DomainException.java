package dev.dimi.erp.shared.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Base de todas as exceções de domínio do Modular ERP POC.
 *
 * <p>Carrega informação suficiente para o {@code GlobalExceptionHandler} mapear para um {@code
 * ProblemDetail} (RFC 9457) consistente:
 *
 * <ul>
 *   <li>{@code code}: identificador kebab-case {@code <dominio>.<motivo>} (ex: {@code
 *       identidade.invalid-credentials}); estável entre versões para client switch.
 *   <li>{@code status}: HTTP status apropriado.
 *   <li>{@code title}: frase humana em PT-BR (cliente decide UX).
 *   <li>{@code properties}: campos extras opcionais (ex: id duplicado, campo violado).
 * </ul>
 *
 * <p>Cada slice de domínio cria suas próprias subclasses (ver {@code
 * identidade.internal.application.*Exception}).
 */
public abstract class DomainException extends RuntimeException {

  private final String code;
  private final HttpStatus status;
  private final String title;

  protected DomainException(String code, HttpStatus status, String title, String detail) {
    super(detail);
    this.code = code;
    this.status = status;
    this.title = title;
  }

  protected DomainException(
      String code, HttpStatus status, String title, String detail, Throwable cause) {
    super(detail, cause);
    this.code = code;
    this.status = status;
    this.title = title;
  }

  public String code() {
    return code;
  }

  public HttpStatus status() {
    return status;
  }

  public String title() {
    return title;
  }

  /** Propriedades adicionais a incluir no payload do {@code ProblemDetail}. Default: vazio. */
  public Map<String, Object> properties() {
    return Map.of();
  }

  /**
   * Headers HTTP a aplicar na resposta. Default: vazio. Usado por exemplo por exceções 429
   * (lockout, rate limit) para incluir {@code Retry-After}.
   */
  public Map<String, String> responseHeaders() {
    return Map.of();
  }
}
