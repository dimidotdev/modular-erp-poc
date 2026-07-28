package dev.dimi.erp.shared.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

/**
 * Entry point chamado pelo Spring Security quando uma request a um endpoint protegido chega sem
 * autenticação válida (ausência de JWT, JWT inválido, autenticação anônima).
 *
 * <p>Resposta segue RFC 9457 (Problem Details) — mesmo formato do {@code GlobalExceptionHandler}.
 * Inclui {@code requestId} e {@code traceId} do MDC para correlação.
 */
@Component
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

  private static final URI PROBLEM_TYPE =
      URI.create("https://example.invalid/erp-poc/problems/identidade/authentication-required");

  private final ObjectMapper objectMapper;

  public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  @Override
  public void commence(
      HttpServletRequest request,
      HttpServletResponse response,
      AuthenticationException authException)
      throws IOException {

    Map<String, Object> body = new LinkedHashMap<>();
    body.put("type", PROBLEM_TYPE.toString());
    body.put("title", "Autenticação requerida");
    body.put("status", HttpStatus.UNAUTHORIZED.value());
    body.put("code", "identidade.authentication-required");
    body.put("detail", "Forneça um access token JWT válido em Authorization: Bearer <token>.");
    body.put("instance", request.getRequestURI());

    String requestId = MDC.get("requestId");
    if (requestId != null) {
      body.put("requestId", requestId);
    }
    String traceId = MDC.get("traceId");
    if (traceId != null) {
      body.put("traceId", traceId);
    }

    response.setStatus(HttpStatus.UNAUTHORIZED.value());
    response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
    response.setCharacterEncoding("UTF-8");
    objectMapper.writeValue(response.getOutputStream(), body);
  }
}
