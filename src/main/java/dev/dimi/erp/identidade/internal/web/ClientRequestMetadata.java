package dev.dimi.erp.identidade.internal.web;

import jakarta.servlet.http.HttpServletRequest;

/** Extrai metadados de rede sem confiar em headers encaminhados fornecidos pelo cliente. */
public final class ClientRequestMetadata {

  private static final int MAX_IP_LENGTH = 64;
  private static final int MAX_USER_AGENT_LENGTH = 512;

  private ClientRequestMetadata() {}

  /**
   * Usa somente o endereço remoto resolvido pelo container. Um proxy confiável deve sobrescrever o
   * endereço na borda; {@code X-Forwarded-For} bruto é deliberadamente ignorado.
   */
  public static String clientIp(HttpServletRequest request) {
    String remoteAddress = request.getRemoteAddr();
    if (remoteAddress == null || remoteAddress.isBlank()) return "unknown";
    String normalized = remoteAddress.trim();
    return normalized.substring(0, Math.min(normalized.length(), MAX_IP_LENGTH));
  }

  public static String userAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    if (userAgent == null || userAgent.isBlank()) return "";
    return userAgent.substring(0, Math.min(userAgent.length(), MAX_USER_AGENT_LENGTH));
  }
}
