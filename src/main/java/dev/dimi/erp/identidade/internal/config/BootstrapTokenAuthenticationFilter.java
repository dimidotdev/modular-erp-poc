package dev.dimi.erp.identidade.internal.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Filter que valida o header {@code X-Bootstrap-Token} contra {@code
 * erp-poc.security.bootstrap.token}. Quando o token é válido, popula o {@code SecurityContext} com
 * a authority {@code ROLE_PLATFORM_BOOTSTRAP}.
 *
 * <p>Só atua em paths {@code /api/v1/platform/**}. Outros paths passam sem mudanças.
 *
 * <p>Quando o token configurado é vazio, qualquer header é rejeitado — endpoint fica efetivamente
 * desabilitado se a configuração não for explícita.
 */
class BootstrapTokenAuthenticationFilter extends OncePerRequestFilter {

  static final String HEADER = "X-Bootstrap-Token";
  static final String ROLE = "ROLE_PLATFORM_BOOTSTRAP";
  private static final String PATH_PREFIX = "/api/v1/platform/";

  private final byte[] expectedToken;
  private final boolean enabled;

  BootstrapTokenAuthenticationFilter(String configuredToken) {
    if (StringUtils.hasText(configuredToken)) {
      this.expectedToken = configuredToken.getBytes(StandardCharsets.UTF_8);
      this.enabled = true;
    } else {
      this.expectedToken = new byte[0];
      this.enabled = false;
    }
  }

  @Override
  protected void doFilterInternal(
      @NonNull HttpServletRequest request,
      @NonNull HttpServletResponse response,
      @NonNull FilterChain chain)
      throws ServletException, IOException {
    if (!request.getRequestURI().startsWith(PATH_PREFIX)) {
      chain.doFilter(request, response);
      return;
    }
    String header = request.getHeader(HEADER);
    if (enabled && StringUtils.hasText(header) && tokensMatch(header)) {
      var auth = new BootstrapAuth();
      auth.setAuthenticated(true);
      SecurityContextHolder.getContext().setAuthentication(auth);
    }
    try {
      chain.doFilter(request, response);
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private boolean tokensMatch(String headerValue) {
    byte[] actual = headerValue.getBytes(StandardCharsets.UTF_8);
    if (actual.length != expectedToken.length) {
      // Chamada dummy para nivelar o tempo gasto e evitar side-channel por tamanho.
      MessageDigest.isEqual(actual, new byte[expectedToken.length]);
      return false;
    }
    // MessageDigest.isEqual é timing-safe quando os arrays têm o mesmo tamanho.
    return MessageDigest.isEqual(actual, expectedToken);
  }

  /**
   * Authentication carregando ROLE_PLATFORM_BOOTSTRAP. Não há principal — é a própria plataforma.
   */
  static final class BootstrapAuth extends AbstractAuthenticationToken {
    BootstrapAuth() {
      super(List.of(new SimpleGrantedAuthority(ROLE)));
    }

    @Override
    public Object getCredentials() {
      return null;
    }

    @Override
    public Object getPrincipal() {
      return "platform-bootstrap";
    }
  }
}
