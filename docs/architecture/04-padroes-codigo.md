# 04 — Padrões de Código

Convenções aplicadas em todo o backend. Não é exaustivo — onde o doc é silente, valem **Google Java Style** + bom senso.

## Idioma

- **PT-BR** para domínio brasileiro: tabelas `pessoa` não, mas conceitos como `cfop`, `ncm`, `cst`, `csosn`, `nfe`, `mvf`, `inscricaoEstadual`. Termos sem tradução natural ficam em PT-BR.
- **Inglês** para infraestrutura técnica: classes, métodos, packages, configs, eventos genéricos (`UserCreatedEvent`, não `UsuarioCriadoEvent`).
- **Commit messages**, **PR titles** e **comentários no código**: PT-BR.
- **Logs estruturados**: chaves em inglês (`tenantId`, `userId`), mensagens em PT-BR (`"venda fechada com sucesso"`).
- **JavaDocs públicas** (raras, só para API expostas a outros módulos): PT-BR.

## Estrutura de pacotes

```
dev.dimi.erp.<modulo>
├── package-info.java         ← @ApplicationModule
├── <ClassesPublicas>.java    ← IDs, eventos, ports (interfaces)
└── internal/
    ├── domain/               ← entidades JPA, VOs internos
    ├── application/          ← services, command handlers
    ├── persistence/          ← repositories
    ├── web/                  ← controllers, DTOs REST
    ├── adapter/              ← adapters de saída (ACBr client, etc)
    └── listener/             ← event listeners
```

Detalhes em [01-arquitetura.md](01-arquitetura.md).

## Naming

### Classes

| Padrão | Exemplo |
|---|---|
| Entidade JPA | `Person`, `Invoice`, `StockMovement` (singular, PascalCase) |
| Repository | `PersonRepository`, `InvoiceRepository` |
| Service de aplicação | `RegisterPersonService`, `EmitInvoiceService` (verbo + agregado) |
| Command (record) | `RegisterPersonCommand` |
| Event (record) | `PersonRegisteredEvent`, `InvoiceAuthorizedEvent` (past tense) |
| Controller | `PersonController`, `InvoiceController` |
| DTO entrada | `CreatePersonRequest`, `UpdateInvoiceRequest`, `PersonFilter` |
| DTO saída | `PersonResponse`, `PersonSummary`, `PersonDetail` |
| Exception customizada | `PersonNotFoundException`, `InvalidCpfException` |
| Port (interface) | `FiscalProvider`, `StockQueryPort` |
| Adapter | `AcbrFiscalProvider`, `JpaStockQueryAdapter` |
| Value Object | `Cpf`, `Cnpj`, `Money`, `Email`, `PersonId` |
| Configuração | `MultiTenancyConfig`, `SecurityConfig` |

### Métodos

- **Verbos no infinitivo** em inglês: `register`, `update`, `cancel`, `find`, `list`.
- **Booleans** começam com `is`/`has`/`can`: `isActive()`, `hasPermission()`, `canCancel()`.
- **Getters/setters via Lombok** (em DTOs/entities), nunca verbos como `getValueOf`.
- **Métodos de query** em repository seguem Spring Data: `findByCpf`, `findByCpfAndActiveTrue`, `existsByEmail`.
- **Use cases / commands**: `RegisterPersonService.register(cmd)`, `CancelInvoiceService.cancel(id, reason)`.

### Variáveis

- `camelCase`, descritivas, sem abreviações ambíguas.
- Constantes: `UPPER_SNAKE_CASE`.
- Loops: `i`, `j`, `idx` aceitáveis; preferir `for-each` ou stream.
- `final` em parâmetros e variáveis locais quando ajudar a leitura — não obrigatório.

## Convenções SQL

- **`snake_case`**: `created_at`, `is_active`, `cpf_cnpj`.
- **Tabelas no singular**: `person`, `branch`, `invoice` (não `persons`).
- **PKs**: sempre `id uuid PRIMARY KEY DEFAULT gen_random_uuid()`.
- **FKs**: `<tabela>_id uuid NOT NULL REFERENCES <tabela>(id) ON DELETE RESTRICT`.
- **Timestamps**: sempre `timestamptz`, com defaults `now()`:
  ```sql
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  ```
- **Soft delete**: `deactivated_at timestamptz` ou `is_active boolean NOT NULL DEFAULT true`.
- **Tipos**:
  - `text` em vez de `varchar(N)`, exceto quando há limite enforced (ex: `cpf char(11)` é OK).
  - **Não usar `char(N)`** — `bpchar` causa bugs com Hibernate `length`.
  - `numeric(15,2)` para valores monetários (até 9999 trilhões), `numeric(15,4)` para impostos.
  - Status como `text` + `CHECK`:
    ```sql
    status text NOT NULL CHECK (status IN ('PENDING','AUTHORIZED','CANCELLED','REJECTED'))
    ```
- **Índices compostos**: nome `idx_<tabela>_<colunas>`:
  ```sql
  CREATE INDEX idx_invoice_status_issued_at ON invoice(status, issued_at DESC);
  ```
- **Índices parciais** quando aplicável:
  ```sql
  CREATE UNIQUE INDEX idx_person_address_default
      ON person_address(person_id) WHERE is_default = true;
  ```
- **`ON DELETE RESTRICT`** é o default — `CASCADE` só quando justificado (ex: `person_address` ao deletar pessoa em soft delete não cascateia; apenas hard delete extraordinário usaria).
- **JSONB** para metadados/configs flexíveis, com índices GIN se filtrados.

## Entidades JPA

```java
@Entity
@Table(name = "person")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "name"})
public class Person {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    @Column(name = "name", nullable = false)
    private String name;

    @Embedded
    private Cpf cpf;

    @Enumerated(EnumType.STRING)   // SEMPRE string, nunca ordinal
    @Column(name = "type", nullable = false)
    private PersonType type;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onPersist() {
        if (id == null) id = UuidCreator.getTimeOrderedEpoch();
        createdAt = updatedAt = Instant.now();
    }

    @PreUpdate
    void onUpdate() { updatedAt = Instant.now(); }

    // construtor de domínio + factory methods
    public static Person register(String name, Cpf cpf, PersonType type) { ... }
}
```

Regras:

- **Lombok `@Data` proibido em entidades JPA** — gera `equals`/`hashCode` baseado em todos os campos, quebra com proxies Hibernate. Use `@Getter` + `@Setter` + `@ToString` manual.
- **`equals`/`hashCode` baseado em ID** (gerado pré-persist), nunca em campos de negócio. Recomendado: `Objects.equals(this.id, other.id)` + `Objects.hash(id)` quando id != null; tratamento de identidade in-memory.
- **Construtor `PROTECTED`** para evitar instanciação sem factory method.
- **`@Version long`** em entidades com leitura concorrente.
- **Sem getters/setters expostos para coleções mutáveis** — retornar `Collections.unmodifiableList(...)` ou usar `@ElementCollection` com cuidado.
- **Coleções inicializadas** (`new ArrayList<>()`) para evitar NPE.

## Value Objects

Records imutáveis com validação no construtor.

```java
public record Cpf(String value) {
    public Cpf {
        Objects.requireNonNull(value, "cpf não pode ser nulo");
        String digits = value.replaceAll("\\D", "");
        if (digits.length() != 11 || !isValid(digits)) {
            throw new InvalidCpfException(value);
        }
        value = digits;  // canoniza para 11 dígitos sem máscara
    }

    public String masked() {
        return "%s.%s.%s-%s".formatted(
            value.substring(0,3), value.substring(3,6),
            value.substring(6,9), value.substring(9,11));
    }

    private static boolean isValid(String digits) { /* DV mod 11 */ }
}
```

Conversão JPA via `@Converter`:

```java
@Converter(autoApply = true)
public class CpfConverter implements AttributeConverter<Cpf, String> {
    public String convertToDatabaseColumn(Cpf c) { return c == null ? null : c.value(); }
    public Cpf convertToEntityAttribute(String s) { return s == null ? null : new Cpf(s); }
}
```

Ou `@Embeddable` se preferir:

```java
@Embeddable
public record Cpf(@Column(name = "cpf", length = 11) String value) { ... }
```

VOs comuns em `dev.dimi.erp.shared.vo`:

- **Identidade**: `Cpf`, `Cnpj`, `CpfCnpj` (sealed interface), `Email`, `PhoneNumber`, `Cep`.
- **Monetário**: `Money` (com scale 2), `Percentage` (scale 4 ou 6).
- **Quantidade**: `Quantity` (scale 3 default, configurável).
- **Período**: `DateRange`, `LocalDateRange`.

## IDs tipados

```java
public record PersonId(UUID value) {
    public PersonId {
        Objects.requireNonNull(value, "person id");
    }

    public static PersonId newId() {
        return new PersonId(UuidCreator.getTimeOrderedEpoch());
    }

    @JsonValue
    public String asString() { return value.toString(); }
}
```

- **Um ID por agregado**.
- **Jackson**: `@JsonValue` serializa como string UUID; deserialização funciona automaticamente para records com 1 campo.
- **Hibernate**: usa `@AttributeOverride` no embedded, ou converter explícito.
- **Equals/hashCode**: gerados pelo record (baseado no `value`).

## DTOs

Records com Jakarta Validation:

```java
public record CreatePersonRequest(
    @NotBlank @Size(min = 2, max = 200) String name,
    @NotBlank @Cpf String cpf,
    @NotNull PersonType type,
    @Email String email,
    @PhoneBR String phone,
    @Valid @NotEmpty List<AddressRequest> addresses
) {}
```

- **Records imutáveis sempre** para DTOs.
- **Jakarta Validation** nos campos.
- **Validadores customizados** em `shared.validation`: `@Cpf`, `@Cnpj`, `@Cep`, `@PhoneBR`.
- **Conversão para command/entity** em `internal/web/mapper/` (manual ou MapStruct).
- **DTO ≠ Entity** — nunca expor entity em endpoint.

## Tratamento de erros

### Hierarquia de exceções

```
DomainException (RuntimeException)
├── ValidationException
│   ├── InvalidCpfException
│   ├── InvalidCnpjException
│   └── ...
├── NotFoundException
│   ├── PersonNotFoundException
│   ├── InvoiceNotFoundException
│   └── ...
├── ConflictException             ← violações de invariantes de negócio
│   ├── DuplicateCpfException
│   ├── InsufficientStockException
│   └── ...
└── ExternalServiceException     ← falhas de integração (ACBr, S3)
    ├── AcbrUnavailableException
    └── ...
```

Cada `DomainException` tem `code` (string, kebab-case): `"person.duplicate-cpf"`, `"stock.insufficient"`, `"acbr.unavailable"`.

### Mapeamento para HTTP

`@ControllerAdvice` global converte para `ProblemDetail` (RFC 9457):

| Exception | Status |
|---|---|
| `ValidationException`, `ConstraintViolationException` | 400 |
| `NotFoundException` | 404 |
| `ConflictException` | 409 |
| `UnauthorizedException` | 401 |
| `ForbiddenException` | 403 |
| `StepUpRequiredException` | 401 (com hint específico) |
| `RateLimitedException` | 429 |
| `ExternalServiceException` | 502 |
| `Exception` (catchall) | 500 (sem detalhes para cliente, log completo) |

Estrutura padrão:

```json
{
  "type": "https://example.invalid/erp-poc/problems/person/duplicate-cpf",
  "title": "CPF já cadastrado",
  "status": 409,
  "code": "person.duplicate-cpf",
  "detail": "O CPF informado já existe para outra pessoa neste tenant.",
  "instance": "/api/v1/cadastros/persons",
  "requestId": "01HXYZ...",
  "errors": [
    { "field": "cpf", "code": "duplicate", "message": "CPF já cadastrado" }
  ]
}
```

Detalhes em [08-api.md](08-api.md).

## Lombok — uso permitido

| Anotação | Onde usar | Onde NÃO usar |
|---|---|---|
| `@Getter`, `@Setter` | DTOs, entidades (com cuidado) | — |
| `@Builder` | DTOs complexos, commands | Entities (use factory methods) |
| `@RequiredArgsConstructor` | Services com final fields (DI) | — |
| `@NoArgsConstructor(access = PROTECTED)` | Entities JPA | — |
| `@ToString(of = {"id","name"})` | Entities/DTOs (exclui coleções) | `@ToString` puro em entity |
| `@EqualsAndHashCode(of = "id")` | Entities | Sem `of` em entity (NPE com proxies) |
| `@Slf4j` | Qualquer classe que loga | — |
| `@Data` | DTOs simples (records preferidos) | **Entities (NUNCA)** |
| `@Value` | Quando record não cabe (interop baseada em beans) | DTOs novos (use record) |

## Convenções de service

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class RegisterPersonService {

    private final PersonRepository persons;
    private final ApplicationEventPublisher events;
    private final Clock clock;       // injetar Clock para teste determinístico

    @Transactional
    public PersonId register(RegisterPersonCommand cmd) {
        if (persons.existsByCpf(cmd.cpf())) {
            throw new DuplicateCpfException(cmd.cpf());
        }
        var person = Person.register(cmd.name(), cmd.cpf(), cmd.type());
        persons.save(person);
        events.publishEvent(new PersonRegisteredEvent(
            person.getId(), TenantContext.tenantId(), Instant.now(clock)));
        log.info("pessoa registrada id={} tipo={}", person.getId(), cmd.type());
        return new PersonId(person.getId());
    }
}
```

Regras:

- **Construtor injection** via `@RequiredArgsConstructor` (final fields).
- **`@Transactional`** na borda do service público.
- **`Clock` injetado** quando service usa `Instant.now()` — facilita teste.
- **Logs no nível certo**: `info` para fluxo normal, `warn` para condições recuperáveis, `error` para falhas com stack trace.
- **Sem `System.out`**, sem `e.printStackTrace()`.

## Convenções de controller

```java
@RestController
@RequestMapping("/api/v1/cadastros/persons")
@RequiredArgsConstructor
@Validated
@Tag(name = "Cadastros / Pessoas")
public class PersonController {

    private final RegisterPersonService registerPerson;
    private final ListPersonsService listPersons;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('pessoa.criar')")
    @Operation(summary = "Registra nova pessoa (PF ou PJ)")
    public PersonResponse register(@RequestBody @Valid CreatePersonRequest req) {
        var id = registerPerson.register(req.toCommand());
        return new PersonResponse(id, /* ... */);
    }

    @GetMapping
    @PreAuthorize("hasAuthority('pessoa.consultar')")
    public CursorPage<PersonSummary> list(
            @ParameterObject PersonFilter filter,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limit) {
        return listPersons.list(filter, cursor, limit);
    }
}
```

Regras:

- **`@PreAuthorize`** em todo método, sem exceção (auditor verifica).
- **`@Valid`** em DTOs de entrada.
- **Paginação cursor-based** (ver [08-api.md](08-api.md)).
- **Sem lógica de negócio** — apenas DTO ↔ command + chamada de service + DTO de resposta.
- **HTTP status corretos**: 200 OK (consulta), 201 Created (criação), 202 Accepted (assíncrono), 204 No Content (delete/update sem body).
- **Anotações OpenAPI** (`@Tag`, `@Operation`) sucintas mas presentes.

## Convenções de repository

```java
public interface PersonRepository extends JpaRepository<Person, UUID>,
                                          JpaSpecificationExecutor<Person> {

    boolean existsByCpf_Value(String cpfValue);

    Optional<Person> findByCpf_Value(String cpfValue);

    @Query("""
        SELECT p FROM Person p
        WHERE p.active = true
          AND (:term IS NULL OR LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%')))
        """)
    Slice<Person> searchActive(@Param("term") String term, Pageable pageable);
}
```

- **Spring Data interface**, sem implementação concreta exceto quando inevitável.
- **Specifications** para queries dinâmicas (filtros opcionais).
- **`@Query` JPQL** para consultas complexas — text blocks para legibilidade.
- **Native queries** apenas quando JPQL não dá conta (window functions, CTEs); marcadas explicitamente.
- **Paginação**: usar `Slice` em vez de `Page` quando não precisa contar total (otimização).
- **`Repository` interfaces ficam em `internal/persistence/`** — não expostas.

## Convenções de migration Flyway

```sql
-- V12__create_person_and_addresses.sql
-- Cria estrutura básica de pessoa (PF/PJ), endereços e contatos.

CREATE TABLE person (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint NOT NULL DEFAULT 0,
    name            text NOT NULL,
    type            text NOT NULL CHECK (type IN ('PF','PJ')),
    cpf             char(11),
    cnpj            char(14),
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_person_document_xor
        CHECK ((type = 'PF' AND cpf IS NOT NULL AND cnpj IS NULL)
            OR (type = 'PJ' AND cnpj IS NOT NULL AND cpf IS NULL))
);

CREATE UNIQUE INDEX uq_person_cpf ON person(cpf) WHERE cpf IS NOT NULL;
CREATE UNIQUE INDEX uq_person_cnpj ON person(cnpj) WHERE cnpj IS NOT NULL;
CREATE INDEX idx_person_name_active ON person(LOWER(name)) WHERE is_active = true;

COMMENT ON TABLE person IS 'Pessoas (físicas e jurídicas) cadastradas no tenant.';
COMMENT ON COLUMN person.type IS 'PF = pessoa física, PJ = pessoa jurídica.';
```

Regras:

- **Nome descritivo**: `V<n>__<acao>_<entidade>.sql`. `n` sequencial dentro de `db/migration/tenant/` (ou `shared/`, etc).
- **Uma tabela ou uma alteração coerente por migration** — facilita revisão e rollback mental.
- **`COMMENT ON`** em tabelas e colunas não-óbvias.
- **DDL idempotente quando seguro** (`CREATE TABLE IF NOT EXISTS`, `CREATE INDEX IF NOT EXISTS`).
- **Sem `DROP` destrutivo** em migration que vai para tenant existente — escrever migration de rename + nova migration depois para drop, com janela de segurança.
- **Sem data seed em migration de `tenant/`** — usar `TenantBootstrapper` em Java (ver [03-multi-tenancy.md](03-multi-tenancy.md)).
- **Migrations em `shared/`** podem ter seed (catálogos globais como `permission`).

## Convenções de teste

Detalhes em [07-testes.md](07-testes.md). Resumo:

- **Naming**: `<metodo>_<situacao>_<resultadoEsperado>`.
  ```java
  @Test
  void register_quandoCpfJaExiste_deveLancarDuplicateCpfException() { ... }
  ```
- **AAA pattern**: Arrange, Act, Assert claramente separados (com linhas em branco).
- **Um `@Test` por cenário** — sem `if/else` ou loops em teste.
- **`@Nested` para agrupar** cenários do mesmo método.
- **AssertJ** para asserções fluentes.
- **Testcontainers** para integração (Postgres real).
- **Builders/fixtures** em `src/test/java/.../fixtures/` para criar entidades de teste sem boilerplate.

## Convenções de log

- **SLF4J + Logback** com encoder JSON.
- **Níveis**:
  - `ERROR` — falhas que precisam de ação humana (raras, alertáveis).
  - `WARN` — condições recuperáveis ou suspeitas (ACBr indisponível com retry, validação falha de input).
  - `INFO` — fluxo de negócio importante (login, cadastro, emissão de NFe).
  - `DEBUG` — detalhes técnicos (queries lentas, decisões internas).
  - `TRACE` — só em dev/troubleshooting.
- **Estrutura**: `log.info("ação ocorreu campo1={} campo2={}", v1, v2)`. Nunca concatenação de string.
- **Sem PII em log**: `cpf={cpf.masked()}` ou `cpf=***`. Email, telefone idem.
- **Nunca log de senha, token, chave** — nem em DEBUG.
- **MDC obrigatório** para `tenantId`, `userId`, `requestId`, `traceId` — populado em filter.

Detalhes em [05-observabilidade.md](05-observabilidade.md).

## Formatação

- **`spotless-maven-plugin`** roda `google-java-format` no `mvn verify`.
- **2 espaços** de indentação (Google style).
- **Linhas até 120 caracteres**.
- **Imports**: sem wildcard imports.
- **Bracket placement**: K&R (chave de abertura na mesma linha).

CI falha PR sem formatação aplicada — rodar `./mvnw spotless:apply` antes de commitar.

## Comentários

- **WHY, não WHAT**. Nome de método bom dispensa explicação do que faz.
- **Sem comentário trivial**: `// registra a pessoa` em cima de `register()` é ruído.
- **Comentário válido**: `// usamos lock pessimista porque a sequência fiscal não tolera duas threads na mesma série`.
- **TODOs com nome**: `// TODO(matheus 2026-06-30): trocar para Redis quando escalar horizontalmente`.
- **JavaDoc** apenas em APIs públicas de outros módulos (raras) ou libs reutilizáveis.

## Imports e visibilidade

- **Sem wildcard imports** (`import java.util.*` proibido).
- **Visibility**:
  - `public` na API pública do módulo (raiz do pacote).
  - **`package-private` (default)** dentro de `internal/`.
  - **`private`** sempre que possível.
  - **`protected`** raramente (factory methods, hooks JPA).
- **Spring beans**: componentes em `internal/` são detectados via `@ComponentScan` mas não vazam pra fora porque a classe é package-private.

## Concorrência

- **Virtual threads habilitados** (`spring.threads.virtual.enabled=true`) — toda request HTTP roda em virtual thread.
- **Bloquear em I/O é OK** — virtual threads são feitas para isso. Não usar `CompletableFuture` por padrão.
- **`@Async`** quando precisar realmente assíncrono (eventos fire-and-forget pós-commit) — usar `TaskDecorator` para propagar `TenantContext`/MDC.
- **`ThreadLocal`** apenas via `TenantContext` (e MDC do Logback). Para outros casos, preferir `ScopedValue` (Java 21+).
- **Locks**: preferir pessimistic na DB para concorrência crítica (estoque, séries fiscais). `ReentrantLock` em memória apenas para caches.

## O que está proibido

- **`System.out.println`** — `log.info(...)` em vez disso.
- **`e.printStackTrace()`** — `log.error("contexto", e)`.
- **Wildcard imports**.
- **`@Data` em entidade JPA**.
- **`@Autowired` em campo** — usar construtor.
- **`ResponseEntity<?>` retornado por padrão** — usar tipos concretos para Swagger funcionar; `ResponseEntity` só quando precisar custmoizar headers/status.
- **Catch `Exception` genérico** sem rethrow específico — só permitido em `@ControllerAdvice` ou na borda de adapter.
- **Banco e ID inseridos manualmente em testes que esperam o `@PrePersist`** — usar builders dos fixtures.
- **`@Transactional` em método `private`** — Spring não intercepta. Idem em método chamado de dentro da mesma classe (proxy bypass).
- **Reflexão em código de produção** sem motivo claro (Jackson/Hibernate já fazem o que precisamos).
- **`new Date()`, `Calendar`** — usar `java.time` (`Instant`, `LocalDate`, `LocalDateTime`).
- **`Optional<X>` como parâmetro** — só como retorno.
- **`Optional<List<X>>`** — retorne `List.of()` vazia.

## Checklist de PR

- [ ] Branch nomeada `dev/<escopo>` saindo da branch correta.
- [ ] `./mvnw verify` verde localmente.
- [ ] Migrations Flyway revisadas (idempotência, sem `DROP` destrutivo).
- [ ] Testes adicionados (unitários + integração se mudou comportamento observável).
- [ ] Audit log emitido para operações sensíveis novas.
- [ ] Eventos de domínio publicados quando há cross-module impact.
- [ ] `@PreAuthorize` em todos os endpoints novos.
- [ ] DTOs com Jakarta Validation.
- [ ] Doc do módulo atualizado (`docs/domain/<modulo>.md`) se mudou contrato.
- [ ] Commit message PT-BR + trailer `Co-Authored-By` se aplicável.
- [ ] Sem secrets/tokens commitados (`gitleaks` no CI).
