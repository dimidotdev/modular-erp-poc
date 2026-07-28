package dev.dimi.erp.identidade.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import java.util.ArrayList;
import java.util.List;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * {@link org.springframework.security.core.Authentication} carregando o {@link AuthenticatedUser}
 * como principal.
 *
 * <p>Authorities expostas para {@code @PreAuthorize}:
 *
 * <ul>
 *   <li>Cada {@code permission} (ex: {@code venda.criar}) vira authority direta para casar com
 *       {@code hasAuthority("venda.criar")}.
 *   <li>Cada {@code role} ganha prefixo {@code ROLE_<code>} (ex: {@code ROLE_OWNER}) para casar com
 *       {@code hasRole("OWNER")}.
 * </ul>
 */
final class JwtAuthentication extends AbstractAuthenticationToken {

  private final AuthenticatedUser principal;

  JwtAuthentication(AuthenticatedUser principal) {
    super(buildAuthorities(principal));
    this.principal = principal;
    setAuthenticated(true);
  }

  @Override
  public AuthenticatedUser getPrincipal() {
    return principal;
  }

  /** Sem credentials carregadas após validação do JWT. */
  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public String getName() {
    return principal.username();
  }

  private static List<GrantedAuthority> buildAuthorities(AuthenticatedUser user) {
    List<GrantedAuthority> authorities =
        new ArrayList<>(user.permissions().size() + user.roles().size());
    for (String permission : user.permissions()) {
      authorities.add(new SimpleGrantedAuthority(permission));
    }
    for (String role : user.roles()) {
      authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
    }
    return List.copyOf(authorities);
  }
}
