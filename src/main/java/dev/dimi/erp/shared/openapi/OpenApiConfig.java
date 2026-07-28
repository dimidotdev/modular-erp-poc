package dev.dimi.erp.shared.openapi;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuração global da especificação OpenAPI 3.0 + Swagger UI gerados pelo {@code
 * springdoc-openapi}.
 *
 * <p>Endpoints expostos:
 *
 * <ul>
 *   <li>{@code /swagger-ui/index.html} — UI interativa (libera por {@code SecurityConfig})
 *   <li>{@code /v3/api-docs} — JSON canônico (importável no Postman/Insomnia)
 *   <li>{@code /v3/api-docs.yaml} — variante YAML
 * </ul>
 *
 * <p>Autenticação: scheme global {@code bearerAuth} (JWT). No Swagger UI clique <b>Authorize</b> e
 * cole o token retornado por {@code POST /api/v1/auth/login} (campo {@code accessToken}). Cada call
 * subsequente envia o header {@code Authorization: Bearer ...} automaticamente.
 */
@Configuration
public class OpenApiConfig {

  private static final String SECURITY_SCHEME = "bearerAuth";

  @Bean
  OpenAPI modularErpPocOpenApi() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Modular ERP POC — Backend API")
                .description(
                    "API REST do Modular ERP POC (multi-tenant schema-per-tenant, Spring Modulith).\n\n"
                        + "**Autenticação:** JWT Bearer. Faça login em "
                        + "`POST /api/v1/auth/login` com `{tenantSlug, username, password}` e "
                        + "use o `accessToken` retornado no botão **Authorize** acima.\n\n"
                        + "**Idempotência:** POSTs que criam estado fiscal/financeiro aceitam o "
                        + "header `Idempotency-Key` — duplicatas retornam o recurso existente.\n\n"
                        + "**Multi-tenancy:** o tenant é resolvido do JWT — não passe `tenantId` "
                        + "em path/query/body.")
                .version("v1")
                .contact(new Contact().name("Matheus Silva").url("https://github.com/dimidotdev")))
        .servers(
            List.of(
                new Server().url("http://localhost:8080").description("Local dev"),
                new Server()
                    .url("https://{host}")
                    .description("Ambiente custom — substituir {host}")))
        .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME))
        .components(
            new Components()
                .addSecuritySchemes(
                    SECURITY_SCHEME,
                    new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description(
                            "JWT emitido por `POST /api/v1/auth/login`. Cole apenas o token "
                                + "(sem o prefixo 'Bearer ').")));
  }
}
