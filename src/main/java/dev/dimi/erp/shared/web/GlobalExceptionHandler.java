package dev.dimi.erp.shared.web;

import dev.dimi.erp.shared.exception.DomainException;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Handler global que traduz exceções em {@code ProblemDetail} (RFC 9457).
 *
 * <p>Doc canônica: {@code docs/architecture/08-api.md}.
 *
 * <ul>
 *   <li>{@link DomainException}: cada subclasse carrega seu próprio {@code code}/{@code
 *       status}/{@code title}.
 *   <li>{@link MethodArgumentNotValidException}: traduz erros do Jakarta Validation em 400 com
 *       {@code errors[]}.
 *   <li>{@link Exception}: catchall em 500 sem vazar detalhes internos.
 * </ul>
 */
@RestControllerAdvice
@Slf4j
class GlobalExceptionHandler {

  private static final URI PROBLEM_BASE = URI.create("https://example.invalid/erp-poc/problems/");

  @ExceptionHandler(DomainException.class)
  ResponseEntity<ProblemDetail> handleDomain(DomainException e) {
    ProblemDetail pd = ProblemDetail.forStatusAndDetail(e.status(), nullSafeDetail(e));
    pd.setTitle(e.title());
    pd.setType(PROBLEM_BASE.resolve(e.code().replace('.', '/')));
    pd.setProperty("code", e.code());
    addCorrelation(pd);
    e.properties().forEach(pd::setProperty);

    ResponseEntity.BodyBuilder builder = ResponseEntity.status(e.status());
    e.responseHeaders().forEach(builder::header);
    return builder.body(pd);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ProblemDetail handleValidation(@NonNull MethodArgumentNotValidException e) {
    List<Map<String, String>> errors = new ArrayList<>();
    for (FieldError fe : e.getBindingResult().getFieldErrors()) {
      Map<String, String> err = new java.util.LinkedHashMap<>();
      err.put("field", fe.getField());
      err.put("code", fe.getCode() != null ? fe.getCode() : "invalid");
      if (fe.getDefaultMessage() != null) {
        err.put("message", fe.getDefaultMessage());
      }
      errors.add(err);
    }
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.BAD_REQUEST, "Um ou mais campos da requisição são inválidos.");
    pd.setTitle("Validação falhou");
    pd.setType(PROBLEM_BASE.resolve("validation"));
    pd.setProperty("code", "validation.failed");
    pd.setProperty("errors", errors);
    addCorrelation(pd);
    return pd;
  }

  /**
   * Spring Security 6+ lança {@link AuthorizationDeniedException} quando {@code @PreAuthorize}
   * recusa o invoke. O subclasse mais antiga {@link AccessDeniedException} cobre cenários do {@code
   * AccessDecisionManager} da API anterior. Em ambos os casos, queremos 403 — não 500.
   */
  @ExceptionHandler({AuthorizationDeniedException.class, AccessDeniedException.class})
  ProblemDetail handleAccessDenied(@NonNull Exception e) {
    log.warn("acesso negado: {}", e.getMessage());
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.FORBIDDEN, "Você não tem permissão para esta operação.");
    pd.setTitle("Acesso negado");
    pd.setType(PROBLEM_BASE.resolve("forbidden"));
    pd.setProperty("code", "forbidden");
    addCorrelation(pd);
    return pd;
  }

  @ExceptionHandler(Exception.class)
  ProblemDetail handleUnexpected(@NonNull Exception e) {
    // Falha não tratada — logamos com stack trace, mas resposta não vaza internals.
    log.error("unexpected error", e);
    ProblemDetail pd =
        ProblemDetail.forStatusAndDetail(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Erro inesperado. Use o requestId para investigação.");
    pd.setTitle("Erro interno");
    pd.setType(PROBLEM_BASE.resolve("internal"));
    pd.setProperty("code", "internal.error");
    addCorrelation(pd);
    return pd;
  }

  private static String nullSafeDetail(DomainException e) {
    return e.getMessage() != null ? e.getMessage() : e.title();
  }

  /** Adiciona requestId/traceId do MDC (populados pelo {@code ObservabilityFilter}) ao payload. */
  private static void addCorrelation(ProblemDetail pd) {
    String requestId = MDC.get("requestId");
    if (requestId != null) {
      pd.setProperty("requestId", requestId);
    }
    String traceId = MDC.get("traceId");
    if (traceId != null) {
      pd.setProperty("traceId", traceId);
    }
  }
}
