# 10 — Identidade e Acesso

Módulo `identidade` — autenticação, autorização, MFA, sessões, RBAC granular, audit hooks.

## Escopo

**Cobre**: tenants (plataforma), usuários, roles e permissions configuráveis, login com Argon2, refresh tokens rotacionáveis, MFA TOTP e WebAuthn, step-up auth em operações sensíveis, lockout progressivo, política de senha, recuperação, switch de filial, audit log de eventos de identidade.

**Não cobre**: SSO/OIDC externo (v1.1), SCIM (v1.1), gerenciamento de funcionário em si (módulo `cadastros`), provisionamento de tenants (operacionalmente exposto em `/platform/...`, lógica no service `TenantProvisioningService` mas a doc deste módulo abrange apenas autenticação dos *usuários* dentro do tenant).

## Conceitos centrais

- **Tenant**: cliente da plataforma com schema dedicado no Postgres ([03-multi-tenancy.md](../architecture/03-multi-tenancy.md)).
- **Usuário (`user_account`)**: pessoa física que acessa o sistema. Tem `username`, `email`, senha hasheada, status.
- **Sessão**: par `(access_token, refresh_token)` emitido no login, identificado por `session_id` (claim `sid`).
- **Permission**: string dot-notation (`nfe.emitir`, `venda.cancelar`). Catálogo global em `shared.permission`.
- **Role**: conjunto nomeado de permissions, configurável por tenant.
- **MFA Factor**: TOTP (RFC 6238) ou WebAuthn passkey.
- **Step-up auth**: reautenticação recente exigida em operações marcadas `is_sensitive=true`.
- **Filial ativa**: filial corrente da sessão (claim `bid`), trocada via endpoint dedicado.

Detalhes operacionais e parâmetros em [../architecture/02-seguranca.md](../architecture/02-seguranca.md).

## Modelo de dados

Tabelas no **schema do tenant** (não em `shared`, exceto `permission`):

```sql
CREATE TABLE user_account (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    username        text NOT NULL,
    email           text NOT NULL,
    full_name       text NOT NULL,
    password_hash   text NOT NULL,            -- argon2id
    status          text NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','DEACTIVATED')),
    must_change_password boolean NOT NULL DEFAULT false,
    last_login_at   timestamptz,
    person_id       uuid REFERENCES person(id),    -- opcional: link com cadastros.Person
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    UNIQUE (lower(username)),
    UNIQUE (lower(email))
);

CREATE TABLE password_history (
    user_id     uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    password_hash text NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, created_at)
);

CREATE TABLE role (
    id            uuid PRIMARY KEY,
    code          text NOT NULL UNIQUE,
    display_name  text NOT NULL,
    description   text,
    is_system     boolean NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE role_permission (
    role_id        uuid NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_code text NOT NULL,                  -- referencia shared.permission(code)
    PRIMARY KEY (role_id, permission_code)
);

CREATE TABLE user_role (
    user_id     uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    role_id     uuid NOT NULL REFERENCES role(id),
    assigned_at timestamptz NOT NULL DEFAULT now(),
    assigned_by uuid REFERENCES user_account(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE user_branch (
    user_id    uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    branch_id  uuid NOT NULL REFERENCES branch(id),
    is_default boolean NOT NULL DEFAULT false,
    PRIMARY KEY (user_id, branch_id)
);
CREATE UNIQUE INDEX uq_user_default_branch
    ON user_branch(user_id) WHERE is_default = true;

CREATE TABLE refresh_token (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL REFERENCES user_account(id),
    session_id      uuid NOT NULL,
    token_hash      text NOT NULL UNIQUE,           -- sha-256 do token entregue
    chain_id        uuid NOT NULL,                  -- toda rotação preserva chain_id
    issued_at       timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    used_at         timestamptz,
    revoked_at      timestamptz,
    user_agent      text,
    ip              inet
);
CREATE INDEX idx_refresh_active ON refresh_token(user_id) WHERE revoked_at IS NULL;

CREATE TABLE mfa_totp (
    user_id              uuid PRIMARY KEY REFERENCES user_account(id) ON DELETE CASCADE,
    secret_encrypted     bytea NOT NULL,             -- AES-256-GCM
    activated_at         timestamptz,
    last_used_at         timestamptz,
    backup_codes_hashed  text[] NOT NULL DEFAULT '{}'
);

CREATE TABLE webauthn_credential (
    id              uuid PRIMARY KEY,
    user_id         uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    credential_id   bytea NOT NULL UNIQUE,
    public_key      bytea NOT NULL,
    sign_count      bigint NOT NULL DEFAULT 0,
    transports      text[],
    attested_at     timestamptz NOT NULL DEFAULT now(),
    last_used_at    timestamptz,
    display_name    text                              -- "Notebook pessoal" definido pelo usuário
);

CREATE TABLE login_attempt (
    id            uuid NOT NULL,
    user_id       uuid,
    username      text NOT NULL,                    -- mesmo que user não exista, registra
    ip            inet NOT NULL,
    success       boolean NOT NULL,
    failure_reason text,
    attempted_at  timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id, attempted_at)
) PARTITION BY RANGE (attempted_at);

CREATE TABLE password_reset_token (
    id          uuid PRIMARY KEY,
    user_id     uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    token_hash  text NOT NULL UNIQUE,
    issued_at   timestamptz NOT NULL DEFAULT now(),
    expires_at  timestamptz NOT NULL,
    used_at     timestamptz
);
```

`shared.permission` é catálogo global; `role`, `user_account`, `user_role`, etc são por tenant. `login_attempt` particionado por dia, retenção 90 dias.

## Estrutura Java

```
dev.dimi.erp.identidade
├── package-info.java                   ← @ApplicationModule, allowedDependencies={"cadastros","shared"}
├── AuthenticatedUser.java              ← record exposto em outros módulos
├── TenantId.java, UserId.java, RoleId.java
├── eventos:
│   ├── UserRegisteredEvent.java
│   ├── UserDeactivatedEvent.java
│   ├── LoginSucceededEvent.java
│   ├── LoginFailedEvent.java
│   ├── RoleAssignedEvent.java
│   ├── PasswordChangedEvent.java
│   ├── MfaEnrolledEvent.java
│   └── StepUpSucceededEvent.java
└── internal/
    ├── domain/        UserAccount, Role, RefreshToken, MfaTotp, WebauthnCredential
    ├── persistence/   *Repository
    ├── application/   AuthenticationService, RegisterUserService, AssignRoleService,
    │                   EnrollMfaService, RotateRefreshService, StepUpService
    ├── web/           AuthController, AdminUserController, RoleController,
    │                   PermissionController, MfaController
    └── listener/      LoginAuditListener (audit log)
```

## Casos de uso e endpoints

### Autenticação

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/auth/login` | público | Login com username/email + senha. Aceita `tenantSlug` no body ou header. Retorna `202` com `mfaChallenge` se MFA ativo, ou `200` com tokens. |
| `POST` | `/api/v1/auth/mfa/verify` | público (com `challengeToken`) | Valida segundo fator. Retorna tokens. |
| `POST` | `/api/v1/auth/refresh` | público (com cookie) | Rotaciona refresh token, emite novo access. |
| `POST` | `/api/v1/auth/logout` | autenticado | Revoga refresh token + sessão atual. |
| `POST` | `/api/v1/auth/logout-all` | autenticado | Revoga todas as sessões do usuário. |
| `POST` | `/api/v1/auth/forgot-password` | público | Inicia reset (envia email se usuário existir; resposta silenciosa). |
| `POST` | `/api/v1/auth/reset-password` | público (com token) | Define senha nova, revoga sessões. |
| `POST` | `/api/v1/auth/change-password` | autenticado | Troca senha autenticada (exige senha atual). |
| `POST` | `/api/v1/auth/step-up` | autenticado | Reautentica (senha+MFA) para operações sensíveis. |
| `POST` | `/api/v1/auth/switch-branch` | autenticado | Troca filial ativa, emite token novo. |
| `GET`  | `/api/v1/auth/me` | autenticado | Dados do usuário corrente + permissions resolvidas + filiais. |

### MFA

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `POST` | `/api/v1/identidade/mfa/totp/enrollment-start` | autenticado | Gera secret + QR code. |
| `POST` | `/api/v1/identidade/mfa/totp/enrollment-confirm` | autenticado | Confirma com primeiro código. Ativa MFA. Gera backup codes. |
| `POST` | `/api/v1/identidade/mfa/totp/disable` | autenticado + step-up | Desativa MFA TOTP. |
| `POST` | `/api/v1/identidade/mfa/webauthn/registration-start` | autenticado | Inicia registro WebAuthn (challenge). |
| `POST` | `/api/v1/identidade/mfa/webauthn/registration-finish` | autenticado | Conclui registro WebAuthn. |
| `DELETE` | `/api/v1/identidade/mfa/webauthn/{credentialId}` | autenticado + step-up | Remove credential. |

### Administração

| Método | Rota | Permissão | Descrição |
|---|---|---|---|
| `GET`  | `/api/v1/identidade/users` | `usuario.consultar` | Lista usuários do tenant. |
| `POST` | `/api/v1/identidade/users` | `usuario.criar` + step-up | Cria novo usuário (senha temporária + force change). |
| `GET`  | `/api/v1/identidade/users/{id}` | `usuario.consultar` | Detalhe. |
| `PATCH`| `/api/v1/identidade/users/{id}` | `usuario.editar` | Atualiza dados básicos. |
| `POST` | `/api/v1/identidade/users/{id}/deactivate` | `usuario.editar` + step-up | Soft delete; revoga sessões. |
| `POST` | `/api/v1/identidade/users/{id}/reactivate` | `usuario.editar` + step-up | Reativa. |
| `POST` | `/api/v1/identidade/users/{id}/reset-password` | `usuario.editar` + step-up | OWNER/MANAGER força reset. |
| `POST` | `/api/v1/identidade/users/{id}/reset-mfa` | `usuario.editar` + step-up | Reset MFA de outro usuário. |
| `POST` | `/api/v1/identidade/users/{id}/roles` | `usuario.permissoes.alterar` + step-up | Atribui role. |
| `DELETE` | `/api/v1/identidade/users/{id}/roles/{roleId}` | idem | Remove role. |
| `POST` | `/api/v1/identidade/users/{id}/branches` | `usuario.editar` | Vincula filial. |
| `GET`  | `/api/v1/identidade/roles` | `role.consultar` | Lista roles do tenant. |
| `POST` | `/api/v1/identidade/roles` | `role.editar` + step-up | Cria role custom. |
| `PUT`  | `/api/v1/identidade/roles/{id}` | `role.editar` + step-up | Atualiza role (não-system). |
| `DELETE` | `/api/v1/identidade/roles/{id}` | `role.editar` + step-up | Remove role custom (system não pode). |
| `GET`  | `/api/v1/identidade/permissions` | `role.consultar` | Lista catálogo global de permissions. |

## Eventos

Publicados na API pública do módulo:

```java
public record UserRegisteredEvent(UserId userId, TenantId tenantId, String username, String email, Instant occurredAt) {}
public record UserDeactivatedEvent(UserId userId, TenantId tenantId, String reason, Instant occurredAt) {}
public record LoginSucceededEvent(UserId userId, TenantId tenantId, UUID sessionId, List<String> amr, String ip, Instant occurredAt) {}
public record LoginFailedEvent(String usernameAttempted, TenantId tenantId, String ip, String reason, Instant occurredAt) {}
public record PasswordChangedEvent(UserId userId, TenantId tenantId, boolean byAdmin, Instant occurredAt) {}
public record MfaEnrolledEvent(UserId userId, TenantId tenantId, String method, Instant occurredAt) {}
public record StepUpSucceededEvent(UserId userId, TenantId tenantId, UUID sessionId, String operation, Instant occurredAt) {}
public record RoleAssignedEvent(UserId userId, RoleId roleId, TenantId tenantId, UserId assignedBy, Instant occurredAt) {}
public record SessionRevokedAllEvent(UserId userId, TenantId tenantId, String reason, Instant occurredAt) {}
```

Consumidos por:

- **`auditoria`** — todos viram eventos `audit_event` (categorias `AUTH`/`ADMIN`).
- **`cadastros`** — `UserRegisteredEvent` opcional para criar `person_id` automaticamente se desejado.

## Regras de negócio

- **RN-IDE-001**: Senha hasheada com Argon2id (parâmetros em [../architecture/02-seguranca.md](../architecture/02-seguranca.md)). Rehash transparente no login se hash usar parâmetros mais fracos.
- **RN-IDE-002**: Política de senha: mínimo 12 caracteres; rejeitar top 10k senhas comprometidas; não conter username/email/full_name.
- **RN-IDE-003**: Histórico das últimas **5 senhas** impede reuso.
- **RN-IDE-004**: Access token JWT TTL **15 min**. Refresh token TTL absoluto **7 dias**, idle TTL **24h**.
- **RN-IDE-005**: Refresh token é **one-shot rotation** — cada uso emite novo + invalida o anterior. Detecção de reuso revoga **toda a chain** (`chain_id`).
- **RN-IDE-006**: Lockout progressivo: 5 falhas/5min → CAPTCHA; 10 falhas/15min → lockout 15min com backoff exponencial até 24h.
- **RN-IDE-007**: IPs com >100 falhas/hora em qualquer usuário entram em denylist temporária (24h) → `429`.
- **RN-IDE-008**: MFA opt-in no v1. Tenant pode forçar MFA para roles específicas via `tenant_setting.mfa_required_for_roles`.
- **RN-IDE-009**: Step-up auth exigido em permissions com `is_sensitive=true`. Janela de 5 min após reautenticação fresh.
- **RN-IDE-010**: Reset de senha do próprio usuário envia email **silenciosamente** se usuário existir; resposta idêntica para usuário existente/inexistente (anti-enumeração).
- **RN-IDE-011**: Reset de senha de outro usuário (admin) exige step-up + permission `senha.resetar.outro-usuario` + envia email ao alvo.
- **RN-IDE-012**: Troca de senha revoga **todas as sessões** do usuário (`logout-all` implícito).
- **RN-IDE-013**: `OWNER` é uma role system; tenant não pode deletar a última atribuição de `OWNER` (deve ter pelo menos 1).
- **RN-IDE-014**: Username e email são case-insensitive (storage lowercase), únicos por tenant.
- **RN-IDE-015**: Trocar filial ativa exige que usuário tenha a filial em `user_branch`. Emite novo access token com claim `bid` atualizada.
- **RN-IDE-016**: Sessão tem **timeout idle implícito** — refresh não usado por 24h é revogado pelo job de limpeza.
- **RN-IDE-017**: Audit log emitido **assíncrono** pelo módulo `auditoria` consumindo eventos — não bloqueia login.
- **RN-IDE-018**: Roles `is_system=true` (OWNER, MANAGER, etc) não podem ser editadas/deletadas; apenas atribuídas.
- **RN-IDE-019**: Catálogo de permissions é **read-only para tenant** — apenas plataforma adiciona.

## Integrações

- **Módulo `cadastros`**: `UserAccount.person_id` opcionalmente vincula a uma `Person` cadastrada (usuário-funcionário). FK soft (validada em service, não no DB para permitir cadastros sem person).
- **Módulo `auditoria`**: consome todos os eventos do `identidade` para gerar audit log persistente.
- **Email transacional** (via `notificacoes` interno ou Azure Communication Services): forgot-password, lockout warning, MFA reset, primeira senha temporária.
- **Microsoft Entra (futuro v1.1)**: SSO/OIDC opcional via OAuth2 Resource Server, autodescobrindo via `iss`/`aud`.

## Critérios de aceitação (BDD-like)

- **Dado** um usuário sem MFA, **quando** ele faz login com credenciais válidas, **então** recebe access+refresh com claims `tid`, `bid`, `roles`, `perms`, `sid`, `amr=["pwd"]`.
- **Dado** um usuário com TOTP, **quando** ele faz login com credenciais válidas, **então** recebe `202` com `challengeToken`, e o segundo POST com TOTP correto retorna tokens com `amr=["pwd","totp"]`.
- **Dado** um refresh válido, **quando** o cliente chama `/auth/refresh`, **então** o token usado é invalidado e um novo refresh + access são emitidos.
- **Dado** um refresh já usado, **quando** o cliente tenta usá-lo de novo, **então** toda a chain é revogada, evento `SuspiciousRefreshReuseEvent` emitido, e cliente recebe `401`.
- **Dado** 10 falhas de login em 15 min, **quando** uma 11ª tentativa ocorre, **então** retorna `429` com `Retry-After`, e novas tentativas em backoff exponencial.
- **Dado** uma operação sensível, **quando** o usuário tenta sem step-up fresh, **então** recebe `401` com `WWW-Authenticate: StepUp` + `stepUpToken`, e após reautenticar pode reenviar.
- **Dado** um OWNER único, **quando** alguém tenta remover sua role `OWNER`, **então** recebe `409 Conflict` com `code=identity.last-owner`.
- **Dado** uma troca de senha, **quando** sucesso, **então** todas as sessões anteriores são revogadas e usuário precisa logar de novo.

## Não-funcionais

- **Latência login p95**: < 800ms (incluindo Argon2 ~1s no pior caso = p99).
- **Disponibilidade `/auth/*`**: 99.95% mensal.
- **MFA enrollment**: < 2s para gerar QR code.
- **Refresh rotation**: < 50ms (não envolve hash de senha).
- **Capacidade**: 50 req/s pico de `/auth/login` por tenant grande sem degradação.

## Roadmap

- **v1.1**: SSO/OIDC (Microsoft Entra, Google Workspace), SCIM provisioning, IP allowlist por tenant, dispositivo confiável (skip MFA por dispositivo).
- **v1.2**: hardware tokens FIDO2 only; anomaly detection em login (ML); risk-based authentication.
- **v2**: customer-managed keys para hashing de senha (BYOK).
