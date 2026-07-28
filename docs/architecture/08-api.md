# 08 — API REST

Convenções universais da API HTTP do Modular ERP POC. Aplica-se a todo endpoint sob `/api/v1/**`.

## Princípios

1. **REST clássico** — recursos no plural, verbos HTTP semânticos, status codes corretos.
2. **Versionada na URL** — `/api/v1/...`. Breaking changes vão para `/api/v2/...`, sem deprecar v1 sem aviso.
3. **JSON com UTF-8** — `Content-Type: application/json; charset=utf-8` em request e response.
4. **Idempotente onde apropriado** — POSTs que criam estado aceitam `Idempotency-Key`.
5. **Paginação cursor-based** — sem offset/limit que gera N+1 ou skip leakage.
6. **Erros padronizados RFC 9457** — Problem Details estruturados.
7. **OpenAPI 3.1 versionado** — contrato é o código + anotações; gerado e commitado.

## Versionamento

### Como versionamos

- **Major na URL** (`/api/v1/`). Compatibilidade quebrada exige v2.
- **Minor/patch implícitos** — novos campos opcionais em response, novos endpoints, alargamento de validação são adicionados sem version bump.
- **Header `X-API-Version: 1.5.2`** opcional na response para clientes sofisticados.

### O que não é breaking change

- Adicionar novo endpoint.
- Adicionar campo **opcional** em request body.
- Adicionar campo em response (clientes devem ignorar campos desconhecidos).
- Relaxar validação (aceitar mais formatos).

### O que é breaking change (exige v2)

- Remover ou renomear endpoint.
- Remover ou renomear campo em response.
- Tornar campo opcional em request obrigatório.
- Apertar validação (rejeitar input antes válido).
- Mudar tipo de campo (string → number).
- Mudar semântica de status code.

### Deprecação

Endpoint depreciado retorna headers:

```
Deprecation: true
Sunset: Fri, 31 Dec 2026 23:59:59 GMT
Link: </api/v2/persons>; rel="successor-version"
```

Janela mínima de **6 meses** entre depreciação e remoção.

## URL design

### Convenções

- **Plurais**: `/persons`, `/invoices`, `/sales`. Sem singulares.
- **Hierarquia clara**: `/branches/{branchId}/users`. Profundidade máxima **3 níveis** (`/a/{x}/b/{y}/c/{z}`); profundidade maior → repensar modelagem.
- **Kebab-case**: `/financial-records`, `/cash-flow-projection`. Sem `camelCase` ou `snake_case` em URLs.
- **Sem extensões**: `/persons/{id}` não `/persons/{id}.json` (negociado por `Accept`).
- **IDs como UUID v7** representados como string padrão UUID.

### Padrões por recurso

```
GET    /api/v1/cadastros/persons              ← lista paginada
POST   /api/v1/cadastros/persons              ← cria
GET    /api/v1/cadastros/persons/{id}         ← detalhe
PUT    /api/v1/cadastros/persons/{id}         ← substituição total
PATCH  /api/v1/cadastros/persons/{id}         ← atualização parcial
DELETE /api/v1/cadastros/persons/{id}         ← soft delete (default)
```

### Ações não-CRUD

Quando o conceito não é CRUD puro, usar **sub-recurso ou ação explícita**:

```
POST   /api/v1/fiscal/invoices/{id}/cancel           ← ação (não DELETE — semântica diferente)
POST   /api/v1/financeiro/installments/{id}/renegotiate
POST   /api/v1/pos/sessions/{id}/close
POST   /api/v1/cadastros/persons/{id}/anonymize
POST   /api/v1/identidade/users/{id}/reset-password
GET    /api/v1/cadastros/persons/{id}/sales-history   ← sub-recurso (consulta agregada)
```

### Namespaces por módulo

| Namespace | Módulo |
|---|---|
| `/auth/**` | Identidade (login, refresh, MFA) |
| `/identidade/**` | Identidade (usuários, roles, permissions) |
| `/cadastros/**` | Cadastros (pessoas, filiais, funcionários, catálogos) |
| `/produtos/**` | Produto |
| `/estoque/**` | Estoque |
| `/movimentacao/**` | Movimentação comercial |
| `/compras/**` | Compras |
| `/financeiro/**` | Financeiro |
| `/fiscal/**` | Fiscal (invoices, certificates, dfe) |
| `/ordens-servico/**` | Ordem de Serviço |
| `/contratos/**` | Contratos |
| `/auditoria/**` | Auditoria (consulta) |
| `/platform/**` | Operações cross-tenant (super-admin) |
| `/actuator/**` | Spring Boot Actuator (porta interna) |

## Verbos HTTP

| Verbo | Uso | Idempotente | Cacheable |
|---|---|---|---|
| `GET` | Consulta. Sem efeito colateral. | Sim | Sim |
| `POST` | Cria ou ação não-idempotente. | Não (mas suporta `Idempotency-Key`) | Não |
| `PUT` | Substituição total do recurso. | Sim | Não |
| `PATCH` | Atualização parcial (JSON Merge Patch). | Sim | Não |
| `DELETE` | Remoção (soft delete default). | Sim | Não |
| `OPTIONS` | CORS pre-flight. | Sim | Não |

## Status codes

### Sucesso

| Código | Quando |
|---|---|
| **200 OK** | GET com sucesso, PUT/PATCH/DELETE com body de resposta. |
| **201 Created** | POST que criou recurso. `Location: /api/v1/.../{id}` obrigatório. |
| **202 Accepted** | Operação assíncrona aceita; corpo descreve como acompanhar. |
| **204 No Content** | Sucesso sem body (PUT/PATCH/DELETE quando não há nada a retornar). |

### Cliente errou

| Código | Quando |
|---|---|
| **400 Bad Request** | Validação de schema falhou (JSON malformado, campo obrigatório ausente). |
| **401 Unauthorized** | Não autenticado, JWT inválido/expirado, ou step-up requerido. |
| **403 Forbidden** | Autenticado mas sem permissão. |
| **404 Not Found** | Recurso não existe (ou usuário não tem acesso e a existência não deve vazar). |
| **405 Method Not Allowed** | Verbo HTTP errado para o recurso. |
| **406 Not Acceptable** | `Accept` pediu formato não suportado. |
| **409 Conflict** | Estado atual conflita com a operação (CPF duplicado, NFe já cancelada). |
| **410 Gone** | Recurso depreciado e removido (futuro). |
| **412 Precondition Failed** | `If-Match` etag não bate (concurrent update). |
| **415 Unsupported Media Type** | `Content-Type` errado. |
| **422 Unprocessable Content** | Sintaticamente válido mas semanticamente inválido (regra de negócio). |
| **428 Precondition Required** | Faltou `If-Match` em operação que exige. |
| **429 Too Many Requests** | Rate limit. Inclui `Retry-After` e `RateLimit-*` headers. |

### Servidor errou

| Código | Quando |
|---|---|
| **500 Internal Server Error** | Bug inesperado. Não vaza stack. |
| **502 Bad Gateway** | Falha em integração externa (ACBr indisponível, S3 timeout). |
| **503 Service Unavailable** | App em manutenção ou capacity issue. Inclui `Retry-After`. |
| **504 Gateway Timeout** | Timeout em integração externa. |

### 400 vs 422

- **400**: a requisição é mal formada estruturalmente (JSON inválido, campo `cpf` ausente).
- **422**: a requisição é válida estruturalmente mas viola regra de negócio (CPF tem formato OK mas DV é inválido; tentou cancelar NFe já cancelada).

Spring 7 renomeou `UNPROCESSABLE_ENTITY` para `UNPROCESSABLE_CONTENT` (RFC 9110). Usar nome novo.

## Erro padronizado — Problem Details (RFC 9457)

Toda response de erro segue o formato:

```http
HTTP/1.1 409 Conflict
Content-Type: application/problem+json
```

```json
{
  "type": "https://example.invalid/erp-poc/problems/person/duplicate-cpf",
  "title": "CPF já cadastrado",
  "status": 409,
  "code": "person.duplicate-cpf",
  "detail": "O CPF 123.456.789-09 já existe para outra pessoa neste tenant.",
  "instance": "/api/v1/cadastros/persons",
  "requestId": "01HXYZ4MZQ8M7VYP3KKHX5J2Z9",
  "traceId": "5f0a3d8c9b4e6e2a",
  "errors": [
    { "field": "cpf", "code": "duplicate", "message": "CPF já cadastrado" }
  ]
}
```

### Campos obrigatórios

- `type` — URL identificando o tipo do problema (não precisa resolver, mas deve ser estável).
- `title` — frase curta humana em PT-BR.
- `status` — código HTTP repetido (conveniência).
- `code` — string kebab-case `<dominio>.<motivo>` para client switch.

### Campos opcionais

- `detail` — descrição contextual (não é mensagem de UX; cliente decide UX).
- `instance` — URI do recurso que causou.
- `requestId`, `traceId` — para suporte correlacionar.
- `errors` — array de field-level errors (validação de DTO).

### Implementação

`@ControllerAdvice` global em `dev.dimi.erp.shared.web.GlobalExceptionHandler` traduz exceções para `ProblemDetail`. Não há excecão tratada caso a caso em controllers.

```java
@ControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateCpfException.class)
    ProblemDetail handle(DuplicateCpfException e, HttpServletRequest req) {
        var pd = ProblemDetail.forStatusAndDetail(CONFLICT,
            "O CPF %s já existe para outra pessoa neste tenant.".formatted(e.cpf().masked()));
        pd.setTitle("CPF já cadastrado");
        pd.setType(URI.create("https://example.invalid/erp-poc/problems/person/duplicate-cpf"));
        pd.setProperty("code", "person.duplicate-cpf");
        pd.setProperty("requestId", MDC.get("requestId"));
        pd.setProperty("traceId", MDC.get("traceId"));
        return pd;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ProblemDetail handle(MethodArgumentNotValidException e) {
        var errors = e.getBindingResult().getFieldErrors().stream()
            .map(fe -> Map.of(
                "field", fe.getField(),
                "code", fe.getCode(),
                "message", fe.getDefaultMessage()))
            .toList();
        var pd = ProblemDetail.forStatusAndDetail(BAD_REQUEST, "Validação falhou");
        pd.setType(URI.create("https://example.invalid/erp-poc/problems/validation"));
        pd.setProperty("code", "validation.failed");
        pd.setProperty("errors", errors);
        pd.setProperty("requestId", MDC.get("requestId"));
        return pd;
    }
}
```

### Catálogo de `code`

Documentado em `docs/api/error-codes.md` (a criar): cada `code` com descrição, status HTTP típico, contexto. Frontend usa como switch para mensagens de UX.

## Paginação

### Cursor-based

```
GET /api/v1/cadastros/persons?limit=20&cursor=<opaque>
```

```json
{
  "items": [ /* até 20 itens */ ],
  "page": {
    "limit": 20,
    "nextCursor": "eyJpZCI6IjAxSFhZWi4uLiIsInRzIjoxNzQ2NTAwMDAwfQ==",
    "hasMore": true
  }
}
```

- **Cursor é opaco** — base64url de `{id, ts, ...}` decodificado server-side.
- **`limit` default 20, max 100**. Validar `@Min(1) @Max(100)`.
- **`hasMore`** indica se há próxima página (consulta `limit + 1` e descarta o extra).
- **Sem `total`** — contagem é cara em tabelas grandes e raramente útil. Endpoint dedicado `GET /persons/count` se necessário.

### Por que não offset/limit

- **N+1 problem**: offset varre N rows mesmo se retornar 20.
- **Skip leakage**: novo registro inserido enquanto pagina pula items.
- **Performance degrada** com offset alto.

Cursor evita os três.

### Ordenação

```
GET /api/v1/cadastros/persons?sort=createdAt:desc,name:asc
```

- Lista de `campo:direcao` separadas por vírgula.
- Campos permitidos enumerados por endpoint (whitelist) — evita ordenar por campo sem índice.
- Default por endpoint quando omitido (geralmente `createdAt:desc`).

## Filtros

Filtros simples como query params:

```
GET /api/v1/cadastros/persons?type=PJ&active=true&q=empresa
```

- **Strings comparáveis**: `eq` implícito (`type=PJ`).
- **Múltiplos valores**: `type=PF,PJ` (lista) ou `type=PF&type=PJ` (repetido).
- **Busca textual**: `q=<termo>` (interpretado pelo backend; geralmente ILIKE em campos indexados).
- **Range**: `createdAfter=2026-01-01&createdBefore=2026-04-30`.

Filtros mapeados para record DTO via `@ParameterObject`:

```java
public record PersonFilter(
    @Schema(allowableValues = {"PF","PJ"}) PersonType type,
    Boolean active,
    String q,
    LocalDate createdAfter,
    LocalDate createdBefore
) {}
```

## Idempotência

### `Idempotency-Key` header

POSTs que criam estado (vendas, pagamentos, emissões fiscais, registros financeiros) **aceitam ou exigem** `Idempotency-Key`:

```
POST /api/v1/financeiro/payments
Idempotency-Key: example-payment-key-001
Content-Type: application/json

{ ... }
```

### Comportamento

1. Backend armazena `(tenant_id, endpoint, idempotency_key) → (request_hash, response_body, status)` em `idempotency_record`.
2. Mesma chave + mesmo body → retorna resposta original.
3. Mesma chave + body diferente → `409 Conflict` com `code=idempotency.key-mismatch`.
4. Retenção: **24h** (configurável). Após, chave é purgada.

### Endpoints que exigem `Idempotency-Key`

- `/financeiro/payments` (batch e individual).
- `/movimentacao/sales` (criação de venda).
- `/fiscal/invoices` (emissão).
- `/contratos/{id}/invoices` (geração de fatura recorrente).

### Endpoints que aceitam (opcional)

- Demais POSTs de criação simples.

## Concorrência otimista (ETag / If-Match)

Updates de recursos com risco de concurrent edit usam ETag:

```http
GET /api/v1/cadastros/persons/{id}
ETag: "5"

PATCH /api/v1/cadastros/persons/{id}
If-Match: "5"
```

- ETag é o `@Version` da entidade (string).
- Update sem `If-Match` em recurso versionado → `428 Precondition Required`.
- Update com `If-Match` desatualizado → `412 Precondition Failed`.

Aplicável a: `person`, `product`, `contract`, `branch_fiscal` (entidades editáveis em UI multi-user). Não aplicável a: registros append-only (`invoice`, `payment`, `audit_event`).

## Negociação de conteúdo

- **`Accept: application/json`** default. Sem suporte a outros media types em v1.
- **`Accept-Language`**: PT-BR e EN. Default PT-BR. Mensagens de erro respeitam (apenas `title` e `detail`; `code` permanece estável).
- **`Accept-Encoding: gzip`**: respostas grandes (lista de items) comprimidas automaticamente.

## CORS

Configurado em [02-seguranca.md](02-seguranca.md). Resumo:

- Origins por tenant (whitelist), nunca `*` em prod.
- `Authorization, Content-Type, Idempotency-Key, X-Tenant-Slug, X-Request-Id, If-Match` permitidos.
- Credentials true (cookies de refresh).

## Headers de request reconhecidos

| Header | Uso |
|---|---|
| `Authorization: Bearer <jwt>` | Autenticação (obrigatório em `/api/v1/**`). |
| `Idempotency-Key: <uuid>` | Idempotência. |
| `If-Match: "<etag>"` | Concorrência otimista. |
| `If-None-Match: "<etag>"` | Cache validation em GET. |
| `Accept: application/json` | Negociação. |
| `Accept-Language: pt-BR, en` | I18n. |
| `Accept-Encoding: gzip` | Compressão. |
| `Content-Type: application/json; charset=utf-8` | Body. |
| `X-Request-Id: <uuid>` | Override de request ID (opcional; backend gera se ausente). |
| `X-Trace-Force: 1` | Forçar amostragem do trace (debug). |
| `X-Tenant-Slug: <slug>` | Tenant em endpoints não-autenticados (login). |

Headers desconhecidos são ignorados (não falham).

## Headers de response

| Header | Sempre |
|---|---|
| `Content-Type: application/json; charset=utf-8` (ou `problem+json` em erros) | Sim |
| `X-Request-Id: <uuid>` | Sim |
| `X-API-Version: 1.x.y` | Sim |
| `Cache-Control: no-store` | Default para endpoints autenticados |
| `ETag: "<version>"` em GET de recurso único | Quando versionado |
| `Location: /api/v1/...` em 201 Created | Sim |
| `Retry-After: <seconds>` em 429/503 | Sim |
| `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset` (draft RFC) | Em 429 |
| `Deprecation: true` + `Sunset: <date>` | Endpoints depreciados |
| `Strict-Transport-Security`, `X-Content-Type-Options`, etc | Sempre (ver [02-seguranca.md](02-seguranca.md)) |

## Datas e horas

- **ISO-8601 UTC** sempre: `2026-05-15T10:30:00Z`.
- Backend nunca emite/aceita timezone local em wire format.
- Frontend converte para timezone do usuário no display.
- **`LocalDate`** para datas sem hora: `2026-05-15`.
- **`Instant`** para timestamps com hora.
- **`LocalDateTime` em wire** proibido (ambíguo sobre TZ).

## Dinheiro

- **`Money` value object** serializado como `{ "amount": "1234.56", "currency": "BRL" }` ou `"1234.56"` (string) quando contexto define a moeda.
- **Sempre string em JSON** — evita perda de precisão por float.
- Scale 2 para valores apresentáveis, 4 para impostos calculados.

## Validação

- **Jakarta Validation** em DTOs (`@NotBlank`, `@Size`, `@Email`, `@Pattern`, `@Min`, `@Max`, custom `@Cpf`).
- **Falhas de validação** retornam 400 com `errors[]` field-level.
- **Validação cruzada** entre campos via `@AssertTrue` em métodos do DTO ou validator class-level.

## OpenAPI

### Geração

- **`springdoc-openapi`** gera spec OpenAPI 3.1 a partir dos controllers + anotações Jakarta + Swagger.
- Spec disponível em `/v3/api-docs` (JSON) e `/swagger-ui.html` (UI) — **apenas em dev/staging**.
- Spec commitada em `docs/api/openapi.yaml` (regenerada em CI; PR falha se divergir).

### Anotações mínimas em controllers

```java
@Tag(name = "Cadastros / Pessoas",
     description = "Gestão de pessoas (PF e PJ) cadastradas no tenant.")
@RestController
@RequestMapping("/api/v1/cadastros/persons")
public class PersonController {

    @Operation(summary = "Registra nova pessoa",
        responses = {
          @ApiResponse(responseCode = "201", description = "Criada com sucesso"),
          @ApiResponse(responseCode = "409", description = "CPF/CNPJ duplicado",
              content = @Content(mediaType = "application/problem+json",
                  schema = @Schema(implementation = ProblemDetail.class)))
        })
    @PostMapping
    PersonResponse register(@RequestBody @Valid CreatePersonRequest req) { ... }
}
```

`@Schema` em campos de DTO descreve constraints e exemplos.

## Webhooks (futuro v1.1)

Não há webhooks no v1. Quando entrar:

- Outbound webhooks com retry exponencial.
- Assinatura HMAC-SHA256 no header `X-Erp-Poc-Signature`.
- Replay protection com timestamp + nonce.
- Subscriptions por tenant via `/api/v1/integrations/webhooks`.

## Internacionalização

V1: PT-BR único. Mensagens em `messages.properties` (Spring MessageSource). Códigos de erro estáveis (`code` em ProblemDetail) — frontend traduz se precisar.

V1.1+: `messages_en.properties` adicional, `Accept-Language` respeitado.

## Catálogo de endpoints (a manter atualizado)

Por módulo, conforme docs/domain/* — cada doc de módulo lista seus endpoints. Inventário consolidado vive em `docs/api/endpoints.md` (gerado por script a partir do OpenAPI).

## Anti-padrões a evitar

- **Verbos em URLs** (`/api/v1/createPerson`). Use POST `/persons`.
- **IDs auto-incrementais expostos** — sempre UUID (não vazar volume).
- **`tenantId` em path** (`/tenants/{tid}/persons`). Tenant vem do JWT.
- **Estado 200 OK com `success: false`** — use 4xx/5xx + ProblemDetail.
- **Mensagens de erro em campo `error: "string"`** — use `ProblemDetail` estruturado.
- **`POST` para consulta** (porque "tem corpo grande"). Use POST `/persons/search` apenas quando query string passar de 2KB.
- **`PUT` parcial** — PATCH para parcial.
- **Body em GET/DELETE** — incompatível com proxies.
- **Versionar via header** apenas (sem URL) — quebra cache, navegação manual.
- **Polling em vez de webhook** — quando volume justifica, oferecer webhook.

## Roadmap

- **v1.1**: webhooks, OAuth2/OIDC para clientes API, SCIM para provisioning, GraphQL gateway opcional para consultas complexas.
- **v1.2**: gRPC para integrações server-to-server de alta performance (relatórios em batch).
- **v2**: REST API v2 com mudanças breaking acumuladas.

## Referências

- RFC 9110 (HTTP Semantics)
- RFC 9457 (Problem Details for HTTP APIs)
- RFC 7240 (Prefer Header)
- RFC 7232 (Conditional Requests)
- IETF draft Idempotency-Key
- IETF draft RateLimit Headers
- Spring Framework 7 ErrorResponse
