package dev.dimi.erp.shared.web;

import com.github.f4b6a3.uuid.UuidCreator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter de observabilidade que roda antes de qualquer outro.
 *
 * <p>Responsabilidades:
 *
 * <ul>
 *   <li>Gera/respeita um {@code X-Request-Id} (UUID v7 se não fornecido pelo cliente).
 *   <li>Coloca {@code requestId} em MDC para logs estruturados.
 *   <li>Devolve o mesmo {@code X-Request-Id} no response para correlação client/server.
 *   <li>Limpa o MDC inteiro no {@code finally} — protege virtual threads reusadas.
 * </ul>
 *
 * <p>{@code tenantId}, {@code userId}, {@code sessionId} são populados em MDC pelo {@code
 * JwtAuthenticationFilter} (no módulo identidade); este filter só cuida do request-level.
 *
 * <p>Doc canônica: {@code docs/architecture/05-observabilidade.md}.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class ObservabilityFilter extends OncePerRequestFilter {

  static final String REQUEST_ID_HEADER = "X-Request-Id";
  static final String REQUEST_ID_MDC_KEY = "requestId";

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {

    String requestId = request.getHeader(REQUEST_ID_HEADER);
    if (requestId == null || requestId.isBlank()) {
      requestId = UuidCreator.getTimeOrderedEpoch().toString();
    }
    MDC.put(REQUEST_ID_MDC_KEY, requestId);
    response.setHeader(REQUEST_ID_HEADER, requestId);

    try {
      chain.doFilter(request, response);
    } finally {
      MDC.clear();
    }
  }
}
