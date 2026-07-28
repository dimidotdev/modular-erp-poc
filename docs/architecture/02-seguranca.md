# 02 — Segurança

Segurança é **primeiro requisito não-funcional**. Decisões aqui são vinculantes e atravessam todos os módulos. Mudança exige ADR.

## Premissas inegociáveis

1. **Confidencialidade por construção** — dados de um tenant nunca chegam a outro. Combina-se com [03-multi-tenancy.md](03-multi-tenancy.md).
2. **Integridade auditável** — toda operação sensível gera evento no audit log. Audit é append-only.
3. **Defesa em profundidade** — autenticação, autorização, validação, isolamento, criptografia, observabilidade. Nenhuma camada isolada é suficiente.
4. **Princípio do menor privilégio** — roles e permissões mínimas necessárias. Step-up para sensíveis.
5. **LGPD por design** — anonimização funcional, consentimento rastreado, portabilidade e eliminação implementadas no v1.
6. **Sem segredos no código** — chaves, senhas, tokens via env vars (Kubernetes Secret em prod, `.env` ignorado em dev).
7. **CVE-free supply chain** — OWASP Dependency-Check no CI, Dependabot semanal, builds com base image atualizada.

## Hash de senhas — Argon2id

**Algoritmo**: Argon2id (variante recomendada pelo OWASP e RFC 9106).

**Parâmetros default** (em `application.yml`):

```yaml
erp-poc:
  security:
    password:
      argon2:
        salt-length: 16
        hash-length: 32
        parallelism: 4
        memory-kb: 65536    # 64 MB
        iterations: 3       # ~1s em hardware moderno
```

**Implementação** — `Argon2PasswordEncoder` do `spring-security-crypto`:

```java
@Bean
PasswordEncoder passwordEncoder(SecurityProperties props) {
    var p = props.password().argon2();
    return new Argon2PasswordEncoder(
        p.saltLength(), p.hashLength(),
        p.parallelism(), p.memoryKb(), p.iterations()
    );
}
```

**Política de senha** (validada na criação e troca):

- Mínimo **12 caracteres**.
- Não pode estar nas **top 10k senhas comprometidas** (validar contra `haveibeenpwned` k-anonymity API offline ou dataset local).
- Não pode conter `username`, `email` local part ou nome completo.
- Sem requisitos de caracteres especiais arbitrários — comprimento + diversidade natural já basta (NIST SP 800-63B).

**Rehash em login**: se hash gravado tem parâmetros mais fracos que os atuais (configuração migrou), rehasher transparente após autenticação bem-sucedida.

**Histórico**: últimas 5 senhas guardadas hash-only para impedir reuso. Tabela `password_history` em schema do tenant.

## Autenticação

### Fluxo de login

1. `POST /api/v1/auth/login` com `{ username | email, password, tenantSlug }`.
2. Backend resolve tenant pelo slug (`demo` → schema `tenant_demo`).
3. Valida credenciais com `PasswordEncoder.matches()`.
4. Se MFA ativo: retorna `202 Accepted` com `mfaChallenge` e `challengeToken` (5min).
5. `POST /api/v1/auth/mfa/verify` com `{ challengeToken, code }` (TOTP ou WebAuthn assertion).
6. Backend emite **access token** (JWT, 15min) e **refresh token** (random 32 bytes, hash sha-256 gravado, 7 dias).

### Access token (JWT)

- Algoritmo: **HS256** com chave simétrica em env var no v1. Migração para **RS256/EdDSA** com keystore rotacionável em v1.1.
- TTL: **15 minutos**.
- Claims:
  ```json
  {
    "iss": "modular-erp-poc",
    "sub": "<user-id>",
    "tid": "<tenant-id>",
    "tslug": "<tenant-slug>",
    "bid": "<active-branch-id>",
    "roles": ["MANAGER"],
    "perms": ["venda.criar","venda.cancelar","nfe.emitir"],
    "amr": ["pwd","totp"],
    "sid": "<session-id>",
    "iat": 1234567890,
    "exp": 1234568790
  }
  ```
- `amr` (Authentication Methods References) registra como o usuário se autenticou (`pwd`, `totp`, `webauthn`, `step-up`).
- `sid` correlaciona logs e permite revogação por sessão.

### Refresh token

- 32 bytes random base64url, **nunca** retornado de novo (one-shot rotation).
- Hash SHA-256 guardado em `refresh_token(tenant_id, user_id, hash, session_id, issued_at, expires_at, used_at, revoked_at)`.
- TTL absoluto **7 dias**. Idle TTL **24h** (último uso > 24h → revoga).
- **Rotação obrigatória**: cada uso emite novo refresh + invalida o anterior.
- **Detecção de reuso**: se token já marcado `used_at` é reutilizado, **revoga toda a chain** da sessão (alguém roubou).
- Armazenamento no cliente: **HttpOnly + Secure + SameSite=Strict cookie** preferencialmente; fallback localStorage só se SPA exigir.

### Logout

- `POST /api/v1/auth/logout` com refresh atual revoga o token + a sessão.
- `POST /api/v1/auth/logout-all` revoga **todas as sessões** do usuário (após troca de senha, MFA reset, suspeita de comprometimento).
- Access token continua válido até TTL (15min) — sem blocklist global em v1 (custo de Redis distribuído). Para revogação imediata em casos críticos: invalidar `sid` no banco e checar em filter (cache local com Caffeine, TTL 1min).

## MFA

### TOTP (RFC 6238)

- Lib: `dev.samstevens.totp:totp:1.7.x`.
- 6 dígitos, janela 30s, hash SHA-1 (compatível com Google Authenticator).
- Secret 160 bits, base32, gravado **cifrado** (AES-256-GCM) em `mfa_totp(user_id, secret_encrypted, issued_at, last_used_at)`.
- Enrollment: usuário escaneia QR code (otpauth URL), confirma com primeiro código antes de ativar.
- **Backup codes**: 10 codes 8-dígitos, hash Argon2 individual, riscáveis uma vez. Mostrados uma única vez no enrollment.

### WebAuthn / Passkeys

- Suporte nativo via Spring Security 7 (`SecurityFilterChain` + `webAuthn()`).
- Múltiplos authenticators por usuário (`webauthn_credential(user_id, credential_id, public_key, sign_count, transports, attested_at)`).
- Discoverable credentials (passkeys) preferenciais.
- User verification = `required` para evitar bypass por dispositivo desbloqueado.

### Política MFA

- **Opcional** no v1, recomendado fortemente. Tenant pode forçar MFA para roles específicos via configuração (`mfa_required_for_roles: ['OWNER','OPERADOR_FINANCEIRO']`).
- **Forçado** para super-admin de tenant (papel `OWNER`) em v1.1.

## RBAC granular por ação de domínio

### Modelo

- **Permission**: string hierárquica dot-notation. Ex: `nfe.emitir`, `nfe.cancelar`, `venda.editar`, `caixa.fechar`, `produto.preco.alterar`.
- **Role**: conjunto nomeado de permissions, configurável por tenant. Ex: `VENDEDOR` = `[venda.criar, venda.consultar, caixa.abrir]`.
- **User → Role(s)**: many-to-many. Usuário pode ter múltiplas roles agregadas.
- **Permission resolvida**: união das permissions de todas as roles do usuário, calculada no login e cacheada na sessão (`perms` claim no JWT).

### Tabelas (em schema do tenant)

```sql
CREATE TABLE role (
    id            uuid PRIMARY KEY,
    code          text NOT NULL,
    display_name  text NOT NULL,
    is_system     boolean NOT NULL DEFAULT false,  -- system roles não editáveis
    created_at    timestamptz NOT NULL DEFAULT now(),
    UNIQUE (code)
);

CREATE TABLE permission (
    code          text PRIMARY KEY,    -- ex: 'nfe.emitir'
    display_name  text NOT NULL,
    description   text,
    is_sensitive  boolean NOT NULL DEFAULT false   -- exige step-up
);

CREATE TABLE role_permission (
    role_id        uuid NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_code text NOT NULL REFERENCES permission(code),
    PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE user_role (
    user_id  uuid NOT NULL,
    role_id  uuid NOT NULL REFERENCES role(id),
    assigned_at timestamptz NOT NULL DEFAULT now(),
    assigned_by uuid,
    PRIMARY KEY (user_id, role_id)
);
```

`permission` é seed compartilhado entre tenants (catálogo de todas as ações conhecidas). `role` e `user_role` são por tenant.

### Roles seed por tenant novo

- **OWNER** — todas as permissions, inclusive sensíveis. Apenas 1-2 por tenant.
- **MANAGER** — operacional + relatórios, sem `*.administrar`.
- **VENDEDOR** — `venda.*`, `caixa.*`, `pessoa.consultar`, `produto.consultar`.
- **OPERADOR_FINANCEIRO** — `financeiro.*`, `pagamento.*`, sem `mvf.cancelar`/`pagamento.estornar` (sensíveis para auditoria).
- **OPERADOR_FISCAL** — `nfe.*`, `dfe.*`, sem cancelamento.
- **OPERADOR_ESTOQUE** — `estoque.*`, `produto.consultar`.
- **CONSULTA** — todas as `*.consultar`.

Tenant pode criar roles custom combinando permissions.

### Uso em controllers

```java
@PreAuthorize("hasAuthority('nfe.emitir')")
@PostMapping("/invoices")
ResponseEntity<InvoiceResponse> emit(@RequestBody @Valid EmitInvoiceRequest req) { ... }

@PreAuthorize("hasAuthority('nfe.cancelar')")
@PostMapping("/invoices/{id}/cancel")
ResponseEntity<Void> cancel(@PathVariable InvoiceId id,
                            @RequestBody @Valid CancelInvoiceRequest req,
                            @RequireFreshAuth Authentication auth) { ... }
```

## Step-up authentication

Operações marcadas como `is_sensitive=true` exigem que o usuário se autentique **dentro dos últimos 5 minutos** com um fator não-bypass-able (senha + MFA, ou WebAuthn).

### Marcações sensíveis (default seed)

- `nfe.cancelar`, `pessoa.anonimizar`, `periodo.congelar`, `usuario.criar`, `usuario.permissoes.alterar`, `role.editar`, `tenant.configurar`, `mfa.resetar`, `senha.resetar.outro-usuario`, `pagamento.estornar`, `mvf.cancelar`, `estoque.ajustar`, `contrato.aditivar`.

### Fluxo

1. Anotação `@RequireFreshAuth` no controller (interceptor verifica claim `auth_time` ou `sid_fresh_until`).
2. Se não fresh: backend retorna `401` com `WWW-Authenticate: StepUp realm="..."` + body `ProblemDetail`:
   ```json
   {
     "type": "https://example.invalid/erp-poc/problems/step-up-required",
     "title": "Reautenticação requerida",
     "status": 401,
     "detail": "Operação sensível exige reautenticação recente.",
     "instance": "/api/v1/invoices/abc/cancel",
     "stepUpToken": "<short-lived-jwt>",
     "expiresIn": 300
   }
   ```
3. Frontend chama `POST /api/v1/auth/step-up` com `{ stepUpToken, password ou mfaAssertion }`.
4. Backend valida, marca a sessão com `fresh_until = now + 5min`, retorna novo access token.
5. Frontend reenviar a operação original com o novo token.

## Bloqueio progressivo de login (lockout)

Tabela `login_attempt(tenant_id, username, ip, success, attempted_at)` particionada por dia.

Política:

- 5 falhas em 5 min do mesmo `(tenant_id, username)` → exigir CAPTCHA.
- 10 falhas em 15 min → lockout 15 min do usuário.
- Backoff exponencial: 15min → 30min → 1h → 2h → 4h → 8h → 24h (cap 24h).
- IPs com >100 falhas/hora em qualquer usuário → adicionar a lista de denylist temporária (24h), retornar `429`.
- Falhas registradas em login_attempt + audit log evento `LOGIN_FAILED`.
- Sucessos resetam contador do `(tenant_id, username)`.

Notificação: ao 3º lockout em 24h, enviar email ao usuário e ao OWNER do tenant.

## CORS

- Configurado em `application.yml` por ambiente.
- Origens permitidas: lista explícita por tenant (`tenant_setting.cors_allowed_origins`). Sem wildcard em produção.
- Headers permitidos: `Authorization, Content-Type, Idempotency-Key, X-Tenant-Slug, X-Request-Id`.
- Methods: `GET, POST, PUT, PATCH, DELETE, OPTIONS`.
- Credentials: `true` (cookies de refresh).
- Max age: 3600.

## CSRF

- API stateless com JWT em header → CSRF desabilitado nos endpoints `/api/**`.
- Refresh token em cookie HttpOnly + SameSite=Strict → SameSite já protege contra CSRF cross-site.
- Endpoints sensíveis (caso futuro de UI server-rendered) usariam token CSRF via `XSRF-TOKEN`/`X-XSRF-TOKEN`.

## Headers de segurança HTTP

Setados via `HeaderWriterFilter` para todas as respostas:

| Header | Valor |
|---|---|
| `Strict-Transport-Security` | `max-age=31536000; includeSubDomains; preload` |
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | `geolocation=(), camera=(), microphone=(), payment=(self)` |
| `Content-Security-Policy` | (apenas para Swagger UI) `default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'` |

## Rate limiting

Bucket por `(tenant_id, user_id)` com **Bucket4j + Redis** ou **in-process Caffeine** (v1: Caffeine, v1.1: Redis para horizontal scale).

Limites padrão:

| Endpoint | Limite |
|---|---|
| `/auth/login` | 10 req/min por IP, 5 req/min por username |
| `/auth/mfa/verify` | 5 req/min por challengeToken |
| `/auth/refresh` | 30 req/min por refresh token |
| `/api/v1/*` (geral) | 600 req/min por user |
| `/api/v1/invoices/emit` | 60 req/min por tenant (proteção contra ACBr) |
| `/api/v1/reports/*` | 30 req/min por user |

Excedido → `429 Too Many Requests` com `Retry-After` e `RateLimit-*` headers (draft RFC).

## Criptografia em repouso

- **Banco**: TDE no Postgres é responsabilidade do provedor cloud (AWS RDS, Google Cloud SQL, Azure Postgres). Self-hosted usa `pgcrypto` em colunas sensíveis.
- **Colunas cifradas com `pgcrypto`** (AES-256-GCM com chave em `pgcrypto` ext + segredo por env):
  - `mfa_totp.secret_encrypted`
  - `webauthn_credential.private_data` (se houver)
  - `tenant_certificate.password_encrypted` (senha do certificado A1)
- **Object storage** (S3/MinIO): SSE-S3 ou SSE-KMS. Buckets versionados, lifecycle de retenção.
- **Backups**: cifrados em repouso. Snapshot diário do RDS + WAL contínuo para PITR.

## Certificado digital A1 (fiscal)

- Upload via endpoint dedicado (`PUT /api/v1/cadastros/branches/{id}/certificate`) com `multipart/form-data` (PFX + senha).
- Armazenado em **object storage** (`tenants/<tenant-id>/certificates/<branch-id>/<sha256>.pfx`), nunca em banco.
- **Senha cifrada** com AES-256-GCM, chave por tenant gerada em provisioning (key wrapping via master KMS).
- **Validade**: `cert_not_after` indexado, job diário alerta 30/15/7 dias antes do vencimento.
- **Revogação**: ao trocar certificado, o anterior é mantido por 90 dias para validação retroativa de NFe, depois purgado.
- **Acesso**: apenas o módulo `fiscal` (via `CertificateProvider` port) — ArchUnit garante.

## LGPD

### Direitos do titular

- **Acesso** (Art. 18 I) — `GET /api/v1/cadastros/persons/{id}/personal-data-export` retorna JSON consolidado dos dados pessoais (com todas as referências cruzadas).
- **Correção** (Art. 18 III) — endpoints CRUD padrão.
- **Anonimização** (Art. 18 IV) — `POST /api/v1/cadastros/persons/{id}/anonymize` substitui `nome` por hash determinístico, `cpf/cnpj` por mascaras genéricas, mantém ID + timestamps para integridade fiscal/financeira. Operação sensível (step-up + audit).
- **Portabilidade** (Art. 18 V) — export em formato estruturado (JSON/CSV) com schema documentado.
- **Eliminação** (Art. 18 VI) — soft delete + plano de purga (após período de retenção fiscal, 5 anos).
- **Consentimento** rastreado em `consent_record(person_id, purpose, given_at, revoked_at, version)`.

### Mapeamento de dados pessoais

Tabela `personal_data_inventory.md` em `docs/architecture/` lista cada coluna com dado pessoal e finalidade — atualizada a cada slice.

### Subprocessadores

Lista de fornecedores que processam dados pessoais (AWS RDS, S3, SendGrid, ACBr provider) mantida em `docs/architecture/subprocessadores.md`, com base legal e país.

## Audit log

Eventos obrigatórios (em todos os módulos):

- `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `MFA_ENROLLED`, `MFA_RESET`, `PASSWORD_CHANGED`, `PASSWORD_RESET`.
- `USER_CREATED`, `USER_DEACTIVATED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`, `PERMISSION_GRANTED`, `PERMISSION_REVOKED`.
- `STEP_UP_REQUIRED`, `STEP_UP_SUCCEEDED`, `STEP_UP_FAILED`.
- `PERSON_ANONYMIZED`, `PERSONAL_DATA_EXPORTED`.
- Por módulo: `INVOICE_AUTHORIZED`, `INVOICE_CANCELLED`, `PAYMENT_PROCESSED`, `PAYMENT_REVERSED`, `PERIOD_FROZEN`, `STOCK_ADJUSTED`, etc.

Detalhes em [05-observabilidade.md](05-observabilidade.md) seção Audit.

## Validação e sanitização de entrada

- **Jakarta Validation** em todos os DTOs de entrada: `@NotBlank`, `@NotNull`, `@Size`, `@Email`, `@Pattern`, custom `@Cpf`, `@Cnpj`.
- **Defesa contra mass-assignment** — DTOs separados de entidades. Nunca usar `@RequestBody Pessoa pessoa`.
- **Defesa contra path traversal** — uploads validam o filename com whitelist regex + magic bytes.
- **Defesa contra SSRF** — adapters HTTP têm allowlist de hosts (`acbr.internal`, `s3.amazonaws.com`, etc); nunca dispatch para URL fornecida pelo usuário.
- **Defesa contra XSS** — saída JSON com `Content-Type: application/json; charset=utf-8`, sem renderização HTML server-side. Frontend é responsável pelo escape no DOM.
- **Defesa contra XXE** — XML parsers (necessários para NFe via ACBr) com `DocumentBuilderFactory` configurado:
  ```java
  factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
  factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
  factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
  factory.setXIncludeAware(false);
  factory.setExpandEntityReferences(false);
  ```

## Segredos e configuração

- **Nada de segredo real no código.** O `application.yml` contém apenas placeholders e defaults locais.
- **Local**: variáveis de ambiente podem ser carregadas pelo shell a partir de um `.env` gitignored.
- **Publicação**: este POC não inclui configuração de infraestrutura nem de ambiente produtivo.
- **Rotação**: a estratégia de rotação do JWT deve ser definida antes de qualquer uso além de demonstração.

## Dependências e supply chain

- **Dependabot** verifica atualizações das dependências declaradas.
- O CI executa Gitleaks, Dependency Review em PRs, formatação, testes unitários, integração e cobertura.
- SCA/SAST/DAST completos, assinatura de artefatos e verificação de imagens ainda não fazem parte deste POC.

## Hardening do runtime

- **Container roda como non-root** (`USER 1000:1000`).
- Manifests de orquestração não fazem parte do projeto. Read-only filesystem, `NoNewPrivileges`, remoção de capabilities e políticas de rede devem ser aplicados pela plataforma de execução.

## Alertas críticos de segurança

| Evento | Severidade | Ação |
|---|---|---|
| 10+ falhas de login do mesmo usuário em 5min | P2 | Slack #sec-alerts + email ao usuário |
| Reuso de refresh token detectado | P1 | Revoga toda sessão + email ao usuário + Slack |
| Step-up falha 3x | P2 | Slack #sec-alerts |
| `5xx` no `/auth/*` > 1% por 5min | P1 | PagerDuty oncall |
| Certificado A1 expira em 7d | P2 | Email OWNER tenant |
| Novo IP geográfico inesperado para usuário OWNER | P3 | Email confirmação |

## Não-funcionais

- **Tempo de hash Argon2**: ~1s (mantém custo de força bruta alto).
- **TTL de access token**: 15min (limita janela de comprometimento).
- **TTL de refresh**: 7d absoluto, 24h idle (compromisso entre UX e exposição).
- **Latência de autenticação**: p95 < 300ms (excluindo hash).
- **Disponibilidade do `/auth/*`**: 99.95% (downtime = ninguém entra).

## Roadmap pós-v1

- **v1.1**: SSO OIDC (Google Workspace, Microsoft Entra), SCIM provisioning, IP allowlist por tenant, audit log search UI.
- **v1.2**: Hardware tokens FIDO2, anomaly detection em login (ML), session anywhere/revogação distribuída via Redis.
- **v2**: Customer-managed encryption keys (BYOK) para clientes enterprise.

## Referências

- OWASP ASVS 4.0
- NIST SP 800-63B (Digital Identity Guidelines)
- RFC 9106 (Argon2)
- RFC 6238 (TOTP)
- LGPD (Lei 13.709/2018)
- ISO/IEC 27001 — controles relevantes
