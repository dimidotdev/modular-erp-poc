# 00 — Stack Técnica

Documento canônico de **versões, libs e justificativas**. Mudanças aqui precisam de revisão arquitetural e atualização do `pom.xml`.

## Princípios de escolha

1. **LTS sempre que possível** — Java 21 LTS, Postgres LTS, libs maduras.
2. **Spring quando faz sentido, lib direta quando Spring traz peso desnecessário** — JJWT em vez de `spring-security-oauth2-jose`, logstash-logback-encoder em vez de stack proprietária.
3. **Open-source e padrões** — sem vendor lock-in em libs core.
4. **Convenção sobre configuração** — Spring Boot starters, defaults sensatos.
5. **Observável e testável por construção** — toda lib core suporta Micrometer/Testcontainers.

## Linguagem e runtime

| Componente | Versão | Justificativa |
|---|---|---|
| **Java** | 21 (LTS) | Suporte Oracle/Adoptium até 2031. Records, pattern matching, virtual threads (Project Loom), sealed types — features que usamos no design. Não-LTS (22-25) descartado para projeto enterprise de longo prazo. |
| **JVM** | Eclipse Temurin 21 | Build oficial OpenJDK, distribuída pela Adoptium, GPL+Classpath. Imagens Docker minimais (`eclipse-temurin:21-jre-jammy`). |

## Framework de aplicação

| Componente | Versão | Justificativa |
|---|---|---|
| **Spring Boot** | 3.5.x (LTS atual no Maven Central) | Versão estável GA, suporte estendido, Java 21 mínimo. Inclui Spring Framework 6.2, Spring Security 6.5, Jakarta EE 10. Migrar para Boot 4 quando virar GA público (estimativa: 2026 H2). |
| **Spring Framework** | 6.2.x (vem com Boot 3.5) | Problem Details (RFC 9457), virtual threads, observation API. |
| **Spring Security** | 6.5.x | OAuth2 Resource Server, Argon2 password encoder, WebAuthn (passkeys), TOTP. |
| **Spring Modulith** | 1.4.x | Compatível com Boot 3.5. `@ApplicationModule`, eventos persistidos, `ApplicationModules.verify()`, OpenAPI por módulo. |
| **Spring Data JPA** | (vem com Boot 3.5) | Repositories tipados, Specifications, paginação. Não usamos QueryDSL (overhead desnecessário em v1). |

## Persistência

| Componente | Versão | Justificativa |
|---|---|---|
| **PostgreSQL** | 16+ | LTS, RLS, particionamento nativo, JSONB, `gen_random_uuid()` nativo, `LISTEN/NOTIFY` para outbox dispatch reativo. Postgres é a única opção testada e suportada. |
| **Hibernate** | 6.6.x (vem com Boot 3.5) | `MultiTenantConnectionProvider` + `CurrentTenantIdentifierResolver` para schema-per-tenant. JPQL, Criteria, `@Filter` para casos específicos. |
| **Flyway** | (vem com Boot starter) | Migrations versionadas. Executadas **por tenant** no provisioning (não no startup global). `V<n>__descricao.sql` em `src/main/resources/db/migration/`. |
| **HikariCP** | (vem com Boot) | Pool default do Spring Boot. Configurado por tenant pool para evitar contenção. |
| **uuid-creator** | `com.github.f4b6a3:uuid-creator:6.x` | UUID v7 ordenado no tempo (`getTimeOrderedEpoch()`) para performance de inserts em índices B-tree. |

## Autenticação e segurança

| Componente | Versão | Justificativa |
|---|---|---|
| **JJWT** | `io.jsonwebtoken:jjwt:0.12.x` | Geração/validação de JWTs leve. Usado para access token (15min) e refresh (hash no banco). Migração para chave assimétrica RS256/EdDSA quando v1.1 exigir múltiplos validadores. |
| **Argon2 (via spring-security-crypto)** | `Argon2PasswordEncoder` | Implementação Spring Security 7 com bouncycastle por baixo. Parâmetros: `saltLength=16`, `hashLength=32`, `parallelism=4`, `memory=65536` (64MB), `iterations=3`. |
| **WebAuthn4j** | (via Spring Security 7) | Passkeys / FIDO2. Suporte nativo a partir do Spring Security 6.4+. |
| **dev.samstevens.totp** | `dev.samstevens.totp:totp:1.7.x` | TOTP RFC 6238 para 2FA. Compatível com Google Authenticator, Microsoft Authenticator, 1Password, Authy. |
| **Spring Security ACL** | **NÃO usar** | Sobreengenharia para nosso RBAC granular. Usamos `@PreAuthorize("hasAuthority('...')")` direto. |

## Observabilidade

| Componente | Versão | Justificativa |
|---|---|---|
| **logstash-logback-encoder** | `net.logstash.logback:logstash-logback-encoder:8.x` | Logs JSON estruturados via Logback. MDC propagado em todos os campos. |
| **Micrometer** | (vem com Boot Actuator) | Facade de métricas. Implementação default Prometheus via `micrometer-registry-prometheus`. |
| **Spring Boot Actuator** | (vem com Boot) | Apenas `/actuator/health/**` e `/actuator/info` estão expostos na configuração base; os demais endpoints são bloqueados. |
| **OpenTelemetry** | `io.opentelemetry:opentelemetry-bom:1.x` | Traces distribuídos. Auto-instrumentation via Java agent (OTel Java Agent), sampling 10% em produção. |
| **MDC + Logback** | (padrão) | Correlation IDs propagados via filter (`tenantId`, `userId`, `traceId`, `requestId`). |

Detalhes em [05-observabilidade.md](05-observabilidade.md).

## API e documentação

| Componente | Versão | Justificativa |
|---|---|---|
| **springdoc-openapi** | `org.springdoc:springdoc-openapi-starter-webmvc-ui:2.x` | OpenAPI 3.1 gerado a partir de controllers + anotações Jakarta Validation. Swagger UI em `/swagger-ui.html` (só dev e staging). |
| **Jackson** | (vem com Boot) | Serialização JSON. Configurar para `failOnUnknownProperties=false` em DTOs de entrada, `failOnEmptyBeans=false` em saída. ISO-8601 para datas, `BigDecimal` como string para precisão. |
| **Problem Details (RFC 9457)** | `org.springframework.web.ErrorResponse` | Built-in no Spring 7. Custom `DomainException` → `ProblemDetail` via `@ControllerAdvice`. |

## Validação

| Componente | Versão | Justificativa |
|---|---|---|
| **Jakarta Validation** | 3.x (vem com Boot) | `@NotBlank`, `@NotNull`, `@Size`, `@Email`, `@Pattern`, `@Min`, `@Max`. |
| **Hibernate Validator** | (impl default) | Implementação de Jakarta Validation. |
| **Validadores customizados** | `dev.dimi.erp.shared.validation.*` | `@Cpf`, `@Cnpj`, `@Cep`, `@PhoneBR`. |

## Testes

| Componente | Versão | Justificativa |
|---|---|---|
| **JUnit 5** | 5.10+ | Padrão. `@DisplayName`, `@Nested`, `@ParameterizedTest`. |
| **AssertJ** | 3.25+ | Asserções fluentes legíveis. Preferir sobre Hamcrest. |
| **Mockito** | 5.x | Mocking de colaboradores. **Evitar** `@MockBean` em integração; preferir fakes/stubs injetados no construtor. |
| **MockMvc / WebTestClient** | (vem com Boot Test) | Testes de controller em `@WebMvcTest` ou integração completa. |
| **Testcontainers** | 1.20+ | **Postgres real** em containers Docker para testes de integração. Sem H2. Reutilização habilitada para velocidade local. |
| **Testcontainers junit-jupiter** | 1.20+ | `@Testcontainers` annotation, `@Container` em estática para sharing. |
| **Spring Modulith Test** | (vem com Modulith) | `@ApplicationModuleTest` para testes por bounded context. `Scenario` API para testar eventos. `ApplicationModules.verify()` para topologia. |
| **ArchUnit** | 1.3+ | Regras de arquitetura testáveis: `internal/` não importável de fora, controllers não acessam repositories direto, etc. |
| **REST Assured** | 5.x | Smoke tests E2E contra app rodando. Uso pontual; preferimos MockMvc. |

## Build e dev tools

| Componente | Versão | Justificativa |
|---|---|---|
| **Maven** | 3.9.x | Familiaridade do time, ecossistema maduro, `mvnw` wrapper checked-in. Gradle foi avaliado e descartado por curva de aprendizado e necessidade de Kotlin DSL. |
| **Maven Surefire** | 3.x | Unit tests. |
| **Maven Failsafe** | 3.x | Integration tests (`*IT.java`). Roda em fase `verify`, não em `test`. |
| **Spring Boot Maven Plugin** | (vem com Boot) | `bootRun`, layered jar para Docker. |
| **Lombok** | 1.18.x | `@Getter`, `@Builder`, `@RequiredArgsConstructor`. **Não usar** `@Data` em entidades JPA (gera `equals`/`hashCode` que quebra com proxies Hibernate). |
| **MapStruct** | 1.5.x+ | Mapeamento DTO ↔ entidade quando o boilerplate justifica. Para mapeamentos simples (≤5 campos), código manual é mais legível. |
| **JaCoCo** | 0.8.x | Cobertura. Meta: ≥80% em pacotes de domínio. |
| **GitHub Dependency Review + Dependabot** | GitHub Actions | Revisa alterações de dependências em PRs e propõe atualizações. Um SCA completo ainda é necessário antes de produção. |
| **Spotless Maven Plugin** | 2.x | Formatação automática (google-java-format). |

## Bibliotecas auxiliares

| Componente | Versão | Justificativa |
|---|---|---|
| **Apache Commons Lang3** | 3.14+ | `StringUtils`, `RandomStringUtils`, `DateUtils`. Uso pontual. |
| **Guava** | 33+ | `ImmutableList`, `Preconditions`, `Cache`. Uso pontual. |
| **Caffeine** | 3.x | Cache local em memória (configurações, permissões resolvidas, tenant context). |
| **Resilience4j** | 2.x | Circuit breaker + retry + bulkhead para integrações externas (ACBr, gateway de pagamento). |
| **OkHttp** | 4.x | Cliente HTTP para ACBr e outros provedores. Connection pooling, timeouts explícitos. |
| **iText 7 (AGPL) ou Apache PDFBox** | a decidir | Geração de PDFs (DANFE preview, recibos). Decisão final no slice Documentos (v1.1). |

## Mensageria e eventos

| Componente | Versão | Justificativa |
|---|---|---|
| **Spring Modulith Events** | (vem com Modulith) | Eventos in-process + persistência outbox em tabela `event_publication`. At-least-once delivery via retry. |
| **Spring Events** | (Spring Framework) | `ApplicationEventPublisher`, `@ApplicationModuleListener`, `@TransactionalEventListener`. |

**Sem Kafka/RabbitMQ no v1.** Externalização para broker fica para v1.2+ quando volume justificar ou módulos virarem serviços separados.

## Integrações externas (v1)

| Sistema | Lib/protocolo |
|---|---|
| **ACBr** | Cliente HTTP via OkHttp. Endpoints REST do ACBr Monitor. Sidecar deployado junto da aplicação. |
| **SMTP** | `spring-boot-starter-mail` (JavaMail). Provider plug-in (SES, SendGrid, Mailgun) via config. |
| **Object Storage** | Azure SDK (`com.azure:azure-storage-blob:12.x`). Azure Blob Storage para anexos e certificados A1 cifrados. Em dev pode usar Azurite (emulador oficial Microsoft) via Testcontainers. |

## Versão Java e JVM tuning

Java 21 LTS, **virtual threads habilitados** (`spring.threads.virtual.enabled=true`). Heap inicial 512MB, max 2GB em prod (ajustável por tenant load). G1GC default. `-XX:+UseStringDeduplication`.

## O que NÃO usamos

- **Quarkus, Micronaut, Helidon** — Spring Boot escolhido pela maturidade, ecossistema e familiaridade do time.
- **Kotlin** — Java 21 com records, pattern matching e virtual threads cobre nossas necessidades; manter time monoglota.
- **GraalVM Native Image** — não tem benefício suficiente para nosso perfil (long-running server, não FaaS).
- **Reactor / WebFlux** — Spring MVC com virtual threads cobre o caso de uso sem complexidade reativa.
- **H2** — testes usam Postgres real via Testcontainers.
- **MapStruct para mapeamentos triviais** — quando ≤5 campos, manual é mais legível.
- **Lombok `@Data` em JPA entities** — gera `equals`/`hashCode` baseado em todos os campos, quebra com proxies Hibernate.
- **Spring Cloud (Eureka, Config Server, Sleuth)** — não temos microserviços. Trace context via OTel é suficiente.
- **JOOQ, QueryDSL** — overhead em v1; JPA + Specifications cobre as queries dinâmicas atuais.

## Atualização de versões

Política de upgrade:

1. **Patch versions** (4.0.x → 4.0.y) — automático via Dependabot, merge se CI verde.
2. **Minor versions** (4.0 → 4.1) — PR manual, revisar release notes.
3. **Major versions** — proposta em ADR (`docs/adr/`), discussão antes de merge.

Dependabot está configurado para abrir PRs semanais. O CI executa Dependency Review em pull requests e Gitleaks no histórico publicado.

## Decisões pendentes

Listadas para revisitar conforme v1 evolui:

- **Iceberg/Delta para dataset analítico** — quando relatórios v2 chegarem.
- **Gerenciador externo de segredos** — selecionar conforme a plataforma de execução; variáveis de ambiente atendem somente ao desenvolvimento local.
- **OAuth2/OIDC externo (SSO Google/Microsoft)** — v1.1, padrão Spring Security 7 já suporta.
- **gRPC para comunicação ACBr** — se Acbr Monitor adicionar gRPC e ganho de latência justificar.
