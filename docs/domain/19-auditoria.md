# 19 — Auditoria

Módulo `auditoria` — audit log unificado, append-only, append-anywhere-consumed-here. Centraliza rastreamento de operações sensíveis cross-module, compliance LGPD/ISO 27001, retenção legal, consulta para investigação.

## Escopo

**Cobre**: persistência de eventos auditáveis (consumidor de eventos de todos os módulos), retenção configurável por categoria, anonimização LGPD após expiração, consulta paginada com filtros, exportação para CSV/JSON, painel de eventos sensíveis (login, anonimização, congelamento, cancelamento NFe).

**Não cobre v1**: alertas dinâmicos baseados em padrões (SIEM-like — v1.1+); detecção de anomalia por ML; tela visual de timeline; correlation automática entre eventos (linkagem por aggregate_id/correlation_id é manual no v1).

## Conceitos centrais

- **Audit Event (`audit_event`)**: registro imutável. Tem categoria, tipo, ator, sujeito (aggregate), payload opcional, contexto (IP, user agent, request_id, trace_id).
- **Categoria**: `AUTH`, `ADMIN`, `FISCAL`, `FINANCIAL`, `STOCK`, `LGPD`, `PLATFORM_ADMIN`, `OS`, `CONTRACT`.
- **Severidade**: `INFO` (rotina), `WARN` (suspeito ou recuperável), `CRITICAL` (alerta humano).
- **Retenção**: política por categoria. Default conservador (5 anos para FISCAL/FINANCIAL, 1 ano para AUTH leitura, 90d para tentativas de login).
- **Append-only**: nenhum `UPDATE` ou `DELETE` na tabela exceto pelo job de retenção (anonimização ou drop de partição).

## Modelo de dados

> **Importante**: schema `audit` é **separado dos schemas de tenant**. É compartilhado entre todos os tenants para facilitar agregação (especialmente para `PLATFORM_ADMIN`). Consultas por tenant são sempre filtradas por `tenant_id`.

```sql
CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.audit_event (
    id              uuid NOT NULL,
    tenant_id       uuid NOT NULL,
    user_id         uuid,                            -- nulo em eventos do sistema
    session_id      uuid,
    occurred_at     timestamptz NOT NULL,

    event_type      text NOT NULL,                    -- 'INVOICE_AUTHORIZED'
    event_category  text NOT NULL CHECK (event_category IN
        ('AUTH','ADMIN','FISCAL','FINANCIAL','STOCK','LGPD','PLATFORM_ADMIN','OS','CONTRACT','SYSTEM')),
    severity        text NOT NULL DEFAULT 'INFO' CHECK (severity IN ('INFO','WARN','CRITICAL')),

    aggregate_type  text,                             -- 'invoice', 'sale', 'person'
    aggregate_id    text,                             -- pode ser uuid ou composta (string)

    actor_ip        inet,
    actor_user_agent text,
    request_id      uuid,
    trace_id        text,
    correlation_id  uuid,                             -- opcional: liga eventos da mesma Saga

    summary         text NOT NULL,                    -- frase humana
    payload         jsonb,                            -- snapshot estruturado opcional

    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Partições mensais criadas via job (pg_partman ou job dedicado em platform.shedlock)
CREATE TABLE audit.audit_event_2026_05 PARTITION OF audit.audit_event
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE INDEX idx_audit_tenant_time     ON audit.audit_event(tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_user_time       ON audit.audit_event(tenant_id, user_id, occurred_at DESC);
CREATE INDEX idx_audit_aggregate       ON audit.audit_event(tenant_id, aggregate_type, aggregate_id);
CREATE INDEX idx_audit_category_time   ON audit.audit_event(tenant_id, event_category, occurred_at DESC);
CREATE INDEX idx_audit_correlation     ON audit.audit_event(correlation_id) WHERE correlation_id IS NOT NULL;
CREATE INDEX idx_audit_payload_gin     ON audit.audit_event USING GIN (payload jsonb_path_ops);

-- Configuração de retenção por categoria
CREATE TABLE audit.retention_policy (
    category        text PRIMARY KEY,
    retain_days     int NOT NULL,
    action_at_eol   text NOT NULL CHECK (action_at_eol IN ('PURGE','ANONYMIZE')),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- Trilha de execução do job de retenção
CREATE TABLE audit.retention_run (
    id              uuid PRIMARY KEY,
    started_at      timestamptz NOT NULL DEFAULT now(),
    finished_at     timestamptz,
    status          text NOT NULL CHECK (status IN ('RUNNING','SUCCESS','FAILED')),
    purged_count    bigint NOT NULL DEFAULT 0,
    anonymized_count bigint NOT NULL DEFAULT 0,
    error_detail    text
);
```

Seed inicial de `retention_policy`:

| Categoria | Dias | Ação |
|---|---|---|
| `AUTH` (login_failed e logins comuns) | 90 | PURGE |
| `AUTH` (login_succeeded + crítico) | 365 | ANONYMIZE |
| `ADMIN` | 1825 (5a) | ANONYMIZE |
| `FISCAL` | 3650 (10a — alinhado ao prazo de retenção fiscal) | ANONYMIZE |
| `FINANCIAL` | 1825 (5a) | ANONYMIZE |
| `STOCK` | 1825 (5a) | ANONYMIZE |
| `LGPD` | 3650 (10a — provando que o titular foi atendido) | ANONYMIZE |
| `PLATFORM_ADMIN` | 1825 (5a) | ANONYMIZE |
| `OS` | 1825 (5a) | ANONYMIZE |
| `CONTRACT` | 1825 (5a) | ANONYMIZE |
| `SYSTEM` | 180 | PURGE |

Anonimização: substitui `payload` por `{"anonymized": true}`, mantém demais campos (categoria, tipo, datas, ator, IDs) para integridade.

## Estrutura Java

```
dev.dimi.erp.auditoria
├── package-info.java   ← allowedDependencies={"shared"} (consome eventos de TODOS via listener)
├── AuditEventId
├── eventos:
│   └── (módulo é principalmente CONSUMER; não publica eventos novos exceto retention)
├── AuditQueryPort.java   ← consultado eventualmente por outros módulos
└── internal/
    ├── domain/        AuditEvent, RetentionPolicy
    ├── persistence/   AuditEventRepository (escrita em audit.audit_event via DataSource separado)
    ├── application/   AuditWriter (componente central — recebe eventos), 
    │                   QueryAuditService, AuditRetentionService, PartitionMaintenanceService
    ├── web/           AuditController (consulta), RetentionPolicyController
    └── listener/      AuthAuditListener, AdminAuditListener, FiscalAuditListener,
                       FinancialAuditListener, StockAuditListener, LgpdAuditListener,
                       OsAuditListener, ContractAuditListener, PlatformAdminAuditListener
```

`AuditWriter` é o único componente que escreve em `audit.audit_event`. Outros módulos **nunca** acessam o schema diretamente — sempre via evento de domínio + listener.

## Casos de uso e endpoints

### Consulta

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/auditoria/events?category=&type=&aggregateType=&aggregateId=&userId=&from=&to=&severity=&q=` | `audit.consultar` |
| `GET`  | `/api/v1/auditoria/events/{id}` | `audit.consultar` (retorna payload completo) |
| `GET`  | `/api/v1/auditoria/events/by-aggregate/{aggregateType}/{aggregateId}` | `audit.consultar` (timeline do agregado) |
| `GET`  | `/api/v1/auditoria/events/export?format=CSV\|JSON&...` | `audit.exportar` + step-up (filtros como acima; limite 100k linhas; gera arquivo em Blob + retorna URL) |

### Sensíveis (acessos diferenciados)

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/auditoria/sensitive-events` | `audit.sensitive.consultar` (filtra severity=CRITICAL ou tipos específicos) |
| `GET`  | `/api/v1/auditoria/lgpd-events` | `lgpd.consultar` (apenas categoria LGPD) |

### Administração da retenção

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/auditoria/retention/policies` | `audit.administrar` |
| `PATCH`| `/api/v1/auditoria/retention/policies/{category}` | `audit.administrar` + step-up |
| `GET`  | `/api/v1/auditoria/retention/runs` | `audit.administrar` |
| `POST` | `/api/v1/auditoria/retention/run-now` | `audit.administrar` + step-up |

### Operações cross-tenant (super-admin)

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/platform/auditoria/events?tenantSlug=...` | `PLATFORM_ADMIN` |
| `POST` | `/api/v1/platform/auditoria/events/correlation/{correlationId}` | `PLATFORM_ADMIN` (timeline da Saga cross-tenant) |

## Catálogo completo de eventos auditáveis

### Categoria `AUTH`

`LOGIN_SUCCEEDED`, `LOGIN_FAILED`, `LOGOUT`, `LOGOUT_ALL`, `MFA_ENROLLED`, `MFA_DISABLED`, `MFA_RESET`, `PASSWORD_CHANGED`, `PASSWORD_RESET`, `STEP_UP_REQUESTED`, `STEP_UP_SUCCEEDED`, `STEP_UP_FAILED`, `SESSION_REVOKED_ALL`, `SUSPICIOUS_REFRESH_REUSE`.

### Categoria `ADMIN`

`USER_CREATED`, `USER_DEACTIVATED`, `USER_REACTIVATED`, `ROLE_CREATED`, `ROLE_EDITED`, `ROLE_DELETED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`, `PERMISSION_GRANTED`, `PERMISSION_REVOKED`, `TENANT_CONFIGURED`, `BRANCH_CREATED`, `BRANCH_FISCAL_UPDATED`, `EMPLOYEE_HIRED`, `EMPLOYEE_TERMINATED`.

### Categoria `FISCAL`

`INVOICE_AUTHORIZED`, `INVOICE_REJECTED`, `INVOICE_CANCELLED`, `INVOICE_CCE_ISSUED`, `INVOICE_INUTILIZED`, `INVOICE_CONTINGENCY_ENTERED`, `INVOICE_CONTINGENCY_RESOLVED`, `CERTIFICATE_UPLOADED`, `CERTIFICATE_REPLACED`, `CERTIFICATE_EXPIRING`, `DFE_DOCUMENT_RECEIVED`, `DFE_MANIFESTED`, `SERIES_INUTILIZATION`, `SPED_EXPORTED`.

### Categoria `FINANCIAL`

`MVF_CREATED`, `MVF_CANCELLED`, `MVF_RENEGOTIATED`, `INSTALLMENT_DUE_DATE_CHANGED`, `PAYMENT_PROCESSED`, `PAYMENT_REVERSED`, `PERIOD_FROZEN`, `PERIOD_UNFROZEN`, `CHART_OF_ACCOUNTS_EDITED`, `COST_CENTER_EDITED`, `BANK_ACCOUNT_EDITED`, `CNAB_REMITTANCE_GENERATED`, `CNAB_RETURN_PROCESSED`, `BANK_RECONCILIATION_MATCHED`.

### Categoria `STOCK`

`STOCK_ADJUSTED`, `STOCK_TRANSFERRED`, `STOCK_INVENTORIED`, `OUTBOUND_VOUCHER_ISSUED`, `STOCK_LOW_ALERT`.

### Categoria `LGPD`

`PERSON_ANONYMIZED`, `PERSONAL_DATA_EXPORTED`, `CONSENT_GIVEN`, `CONSENT_REVOKED`, `DATA_PORTABILITY_REQUESTED`.

### Categoria `PLATFORM_ADMIN`

`TENANT_PROVISIONED`, `TENANT_SUSPENDED`, `TENANT_TERMINATED`, `TENANT_MIGRATION_APPLIED`, `IMPERSONATION_STARTED`, `IMPERSONATION_ENDED`, `PLATFORM_ADMIN_ACTION`.

### Categoria `OS`

`OS_OPENED`, `OS_STATUS_CHANGED`, `OS_BILLED`, `OS_CLOSED`, `OS_CANCELLED`, `OS_WORKFLOW_CONFIGURED`.

### Categoria `CONTRACT`

`CONTRACT_CREATED`, `CONTRACT_ACTIVATED`, `CONTRACT_SUSPENDED`, `CONTRACT_TERMINATED`, `CONTRACT_AMENDED`, `CONTRACT_ADJUSTED`, `CONTRACT_INVOICE_GENERATED`, `CONTRACT_CYCLE_FAILED`.

### Categoria `SYSTEM`

`MIGRATION_APPLIED`, `BACKUP_COMPLETED`, `RETENTION_RUN`, `JOB_FAILED`, `INTEGRATION_FAILURE`.

## Regras de negócio

- **RN-AUD-001**: `audit_event` é **append-only**. Permissões de banco para `app_role`: `INSERT`, `SELECT`. **`UPDATE`/`DELETE` revogados**. Apenas `audit_role` (usado pelo job de retenção) tem `UPDATE` para anonimização e `DELETE` para drop de partição.
- **RN-AUD-002**: Eventos são escritos por listeners `@ApplicationModuleListener` (em transação separada do publisher). Falha de escrita do audit **não bloqueia** o fluxo de negócio — log + retry em event_publication outbox.
- **RN-AUD-003**: `summary` é obrigatório, ≤ 500 caracteres, frase humana em PT-BR. `payload` opcional, JSONB com snapshot before/after quando faz sentido.
- **RN-AUD-004**: `correlation_id` opcional permite agrupar eventos de uma mesma Saga (cancelamento de venda gera 4-5 eventos com mesmo `correlation_id`).
- **RN-AUD-005**: Particionamento mensal automatizado por job (`PartitionMaintenanceService`). Cria partição do mês +1 antecipadamente; drop de partições além da retenção máxima.
- **RN-AUD-006**: Job diário de retenção (`AuditRetentionService`):
  - Itera por categoria conforme `retention_policy`.
  - Para `ANONYMIZE`: `UPDATE` payload de eventos com `occurred_at < now() - retain_days` setando `{"anonymized": true}`.
  - Para `PURGE`: identifica partições inteiras passadas, `DROP PARTITION` para eficiência.
  - Cada execução registra em `retention_run`.
- **RN-AUD-007**: Consultas filtram **obrigatoriamente** por `tenant_id` (exceto endpoints `/platform/*` com role `PLATFORM_ADMIN`).
- **RN-AUD-008**: Permission `audit.consultar` dá acesso a categorias `INFO` + `WARN`. Categorias `CRITICAL` (refresh reuse, anonymization, period freeze, certificate change) exigem `audit.sensitive.consultar`.
- **RN-AUD-009**: Export gera arquivo em Blob com URL pré-assinada de 1h. Tamanho máx 100k linhas. Export > 100k → erro `audit.export-too-large` + sugestão de filtros mais estreitos.
- **RN-AUD-010**: Alteração de `retention_policy` requer step-up + `audit.administrar`. Audit log meta-evento.
- **RN-AUD-011**: Tentativa de `UPDATE`/`DELETE` por `app_role` **falha por permissão** no Postgres. Defesa em profundidade (mesmo se aplicação bugar).
- **RN-AUD-012**: `actor_ip` armazenada como `inet`; ofuscação IPv4 do último octeto / IPv6 dos últimos 80 bits após `retain_days` (configurável).

## Integrações

- **Todos os módulos** publicam eventos consumidos aqui. Lista em "Catálogo completo de eventos auditáveis".
- **Azure Blob Storage**: exports em CSV/JSON.
- **Azure Key Vault**: opcional para cifrar `payload` sensível (v1.1+; v1 mantém em JSONB direto).

## Critérios de aceitação

- **Dado** um login bem-sucedido, **quando** o evento `LoginSucceededEvent` é publicado pelo módulo `identidade`, **então** `AuthAuditListener` grava um `audit_event` com categoria `AUTH`, tipo `LOGIN_SUCCEEDED`, severity `INFO`, summary contextual, payload com `amr` e `sessionId`.
- **Dado** um cancelamento de venda em Saga (cancela venda + estoque + financeiro + fiscal), **quando** todos os 4-5 eventos são emitidos, **então** todos têm o mesmo `correlation_id`, e consulta `/by-aggregate/sale/{id}` retorna timeline correlata.
- **Dado** um event de anonimização LGPD, **quando** publicado, **então** registrado com severity `CRITICAL` (auditoria perpétua) e payload com IDs (não com dados pessoais originais).
- **Dado** retenção 90 dias para `LOGIN_FAILED`, **quando** job de retenção roda, **então** partições mensais com dados além de 90 dias são droppadas; `retention_run` registra contagem.
- **Dado** retenção 5 anos com anonimização para `INVOICE_AUTHORIZED`, **quando** job processa um evento com 5 anos + 1 dia, **então** seu `payload` vira `{"anonymized": true}`; demais campos intactos.
- **Dado** usuário comum tenta consultar audit, **quando** sem permission `audit.consultar`, **então** `403 forbidden`.
- **Dado** consulta com filtro de categoria `FISCAL` no último mês, **quando** GET, **então** retorna paginação cursor com eventos da categoria e período.
- **Dado** tentativa de `UPDATE` direto em `audit_event` por aplicação, **quando** SQL emitido, **então** Postgres rejeita por falta de permissão (`ERROR: permission denied`).
- **Dado** export de 50k eventos, **quando** request, **então** arquivo CSV criado em Blob, URL retornada com expiry 1h.

## Não-funcionais

- **Latência escrita**: < 5ms por evento (transação curta, listener async).
- **Latência consulta filtrada por (tenant, time, category)**: p95 < 200ms para janelas até 30 dias com índices apropriados.
- **Volume esperado**: ~100 events/min por tenant em uso ativo → 50M events/mês para 1000 tenants ativos. Particionamento mensal evita degradação.
- **Storage**: ~500 bytes médio por evento. 50M/mês = 25GB/mês. Cool tier para partições > 90d, archive para > 1ano.
- **Não bloqueia fluxo de negócio**: outbox + listener garantem que escrita falha não derruba operação primária.
- **Disponibilidade leitura**: 99.9%.

## Roadmap

- **v1.1**: alertas dinâmicos baseados em padrões (X eventos do tipo Y em janela Z → notify); painel de timeline visual; export para SIEM externo (Splunk, ELK).
- **v1.2**: cifração do `payload` para eventos contendo PII via Key Vault; detecção de anomalia por ML (login atípico, padrão de pagamento incomum).
- **v2**: query language custom (audit-ql) para investigações complexas; integration com Microsoft Sentinel.

## Anti-padrões a evitar

- **Audit log na mesma tabela que dados operacionais** — performance e isolamento ruins.
- **Audit log mutável** — auditor não pode auditar log que muda.
- **Log de senhas, tokens, certificados** em `payload` — mesmo com retenção, vazamento legal.
- **Síncrono na transação primária** — outbox + listener async; falha de audit ≠ falha de negócio.
- **Sem particionamento** — tabela vai a TB rápido, queries degradam, retenção impossível.
- **Tags sem categoria/severity** — busca fica inviável.
- **Endpoints sem `tenant_id` mandatório** — vazamento cross-tenant trivial.
