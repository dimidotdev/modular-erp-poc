package dev.dimi.erp.identidade.internal.config;

import dev.dimi.erp.identidade.internal.application.JwtTokenValidator;
import dev.dimi.erp.identidade.internal.web.JwtAuthenticationFilter;
import dev.dimi.erp.shared.web.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Configuração baseline de segurança HTTP.
 *
 * <p>Stack após Etapa 7:
 *
 * <ol>
 *   <li>{@code ObservabilityFilter} (shared, ORDER_HIGHEST_PRECEDENCE) — popula {@code requestId}
 *       em MDC e devolve no header {@code X-Request-Id}.
 *   <li>{@code BootstrapTokenAuthenticationFilter} — header {@code X-Bootstrap-Token} para {@code
 *       /api/v1/platform/**}.
 *   <li>{@link JwtAuthenticationFilter} — header {@code Authorization: Bearer <jwt>} popula {@code
 *       SecurityContext} com {@code AuthenticatedUser}, {@code TenantContext} e MDC tenant-scoped.
 *   <li>{@code AuthorizationFilter} (Spring Security) — aplica {@code @PreAuthorize} e {@code
 *       .requestMatchers().authenticated()}.
 * </ol>
 *
 * <p>Endpoints anônimos: {@code /actuator/health/**}, {@code /actuator/info}, {@code
 * /api/v1/auth/{login,refresh,logout,logout-all}}. Resto exige autenticação.
 */
@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
class SecurityConfig {

  @Bean
  SecurityFilterChain securityFilterChain(
      HttpSecurity http,
      IdentidadeProperties props,
      JwtTokenValidator jwtValidator,
      RestAuthenticationEntryPoint authEntryPoint)
      throws Exception {

    var bootstrapFilter = new BootstrapTokenAuthenticationFilter(props.bootstrap().token());
    var jwtFilter = new JwtAuthenticationFilter(jwtValidator);

    return http.csrf(csrf -> csrf.disable())
        .formLogin(form -> form.disable())
        .httpBasic(basic -> basic.disable())
        .logout(logout -> logout.disable())
        .headers(
            headers ->
                headers
                    .frameOptions(frame -> frame.deny())
                    .referrerPolicy(
                        policy ->
                            policy.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                    .permissionsPolicyHeader(
                        policy ->
                            policy.policy("camera=(), microphone=(), geolocation=(), payment=()")))
        .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .exceptionHandling(eh -> eh.authenticationEntryPoint(authEntryPoint))
        .authorizeHttpRequests(
            auth ->
                auth.requestMatchers("/actuator/health/**", "/actuator/info")
                    .permitAll()
                    .requestMatchers("/actuator/**")
                    .denyAll()
                    // Swagger UI + OpenAPI JSON: anônimos quando habilitados. A configuração base
                    // os desliga; o profile local os ativa para exploração da API. Endpoints
                    // documentados continuam exigindo JWT.
                    .requestMatchers(
                        "/swagger-ui.html",
                        "/swagger-ui/**",
                        "/v3/api-docs",
                        "/v3/api-docs/**",
                        "/v3/api-docs.yaml")
                    .permitAll()
                    // /auth/login é anônimo (produz o JWT). /auth/refresh, /auth/logout e
                    // /auth/logout-all também são anônimos do ponto de vista do Spring Security:
                    // a validação do refresh token apresentado acontece dentro do service.
                    .requestMatchers(
                        "/api/v1/auth/login",
                        "/api/v1/auth/refresh",
                        "/api/v1/auth/logout",
                        "/api/v1/auth/logout-all")
                    .permitAll()
                    .requestMatchers("/api/v1/platform/**")
                    .hasRole("PLATFORM_BOOTSTRAP")
                    .anyRequest()
                    .authenticated())
        // JWT filter na posição "where username/password auth would normally go".
        // Bootstrap filter logo antes do AuthorizationFilter (mais tarde na chain).
        .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(bootstrapFilter, AuthorizationFilter.class)
        .build();
  }
}
