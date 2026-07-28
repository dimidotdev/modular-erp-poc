# 03 — Multi-tenancy

Estratégia de isolamento entre clientes (tenants) do Modular ERP POC. Decisão arquitetural mais impactante depois da modularização.

## Decisão

**Schema-per-tenant no Postgres**, gerenciado via **Hibernate `MultiTenantConnectionProvider` + `CurrentTenantIdentifierResolver`**.

- Cada tenant tem **um schema Postgres dedicado** (`tenant_demo`, `tenant_example`, etc).
- Tabelas compartilhadas (catálogo de permissions, índice de tenants, audit log central) vivem em schemas próprios (`shared`, `audit`, `platform`).
- **Sem coluna `tenant_id`** em tabelas de domínio — o schema **é** o isolamento.
- Resolução de tenant ocorre **uma vez por requisição** no filter de autenticação e fica no `TenantContext` (ThreadLocal).

## Por que não shared schema com `tenant_id`

Comparativo registrado para futura referência (ADR-001):

| Critério | Shared schema + `tenant_id` | Schema-per-tenant (escolhido) |
|---|---|---|
| Isolamento físico | Nenhum (depende de RLS + disciplina) | Total — Postgres isola schemas |
| Acidente cross-tenant | Bug deixa vazar | Impossível por construção (schema diferente) |
| Performance de queries | Índices precisam começar por `tenant_id` | Estatísticas separadas por schema |
| Backup/restore por tenant | Custom (export filtrado por `tenant_id`) | `pg_dump --schema=tenant_X` |
| Migration por tenant | Aplicada uma vez para todos | Aplicada por tenant (uma run por schema) |
| Customização por tenant | Difícil (mesma tabela) | Possível (schema diferente pode ter colunas extras) |
| Limite operacional | Milhões de tenants | ~500-1000 por database |
| Operação inicial | Mais simples | Complexa — provisioner, multi-Flyway |
| LGPD/portabilidade | Export filtrado, eliminação custosa | `DROP SCHEMA tenant_X CASCADE` |
| Custo de mudança no futuro | Difícil (refatorar todo SQL) | Mais fácil (consolidar schemas) |

Para nosso perfil (PMEs brasileiras, ~10-500 tenants por instalação, isolamento fiscal crítico), **schema-per-tenant ganha**. Quando uma instalação ultrapassar 500 tenants, escalamos horizontalmente para mais databases (sharding por hash do tenant_id).

## Topologia de schemas

```
postgres database "modular_erp_poc"
├── shared           ← schemas e tabelas de plataforma
│   ├── tenant       ← índice de tenants registrados
│   ├── permission   ← catálogo global de permissions de domínio
│   └── system_config
├── audit            ← audit log central (tabelas particionadas por mês)
│   ├── audit_event
│   └── audit_event_payload
├── tenant_demo      ← schema completo do tenant "demo"
│   ├── role, user_account, person, branch, product, invoice, ...
│   ├── flyway_schema_history
│   └── event_publication  (Modulith outbox, por tenant)
├── tenant_fooltda
│   └── ...
└── platform         ← schemas operacionais (cron jobs, locks distribuídos, etc)
    ├── shedlock
    └── outbox_dispatcher
```

### Schema `shared`

Tabelas globais à instalação:

```sql
CREATE TABLE shared.tenant (
    id                  uuid PRIMARY KEY,
    slug                text NOT NULL UNIQUE,        -- identificador URL-friendly
    schema_name         text NOT NULL UNIQUE,        -- "tenant_demo"
    display_name        text NOT NULL,
    legal_name          text NOT NULL,
    cnpj                text UNIQUE,
    status              text NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','PROVISIONING','TERMINATED')),
    plan                text NOT NULL,
    provisioned_at      timestamptz,
    suspended_at        timestamptz,
    terminated_at       timestamptz,
    config              jsonb NOT NULL DEFAULT '{}'::jsonb,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE shared.permission (
    code          text PRIMARY KEY,    -- 'nfe.emitir'
    display_name  text NOT NULL,
    description   text,
    is_sensitive  boolean NOT NULL DEFAULT false
);

CREATE INDEX idx_tenant_slug_active ON shared.tenant(slug) WHERE status='ACTIVE';
```

### Schema `audit`

Append-only, particionado por mês:

```sql
CREATE TABLE audit.audit_event (
    id              uuid NOT NULL,
    tenant_id       uuid NOT NULL,
    user_id         uuid,
    session_id      uuid,
    occurred_at     timestamptz NOT NULL,
    event_type      text NOT NULL,           -- 'INVOICE_CANCELLED'
    aggregate_type  text,                    -- 'invoice'
    aggregate_id    text,
    actor_ip        inet,
    actor_user_agent text,
    request_id      uuid,
    trace_id        text,
    payload         jsonb,                    -- snapshot opcional
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE INDEX idx_audit_event_tenant_time
    ON audit.audit_event (tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_event_actor
    ON audit.audit_event (tenant_id, user_id, occurred_at DESC);
CREATE INDEX idx_audit_event_aggregate
    ON audit.audit_event (tenant_id, aggregate_type, aggregate_id);
```

Detalhes da auditoria em [05-observabilidade.md](05-observabilidade.md) e [../domain/19-auditoria.md](../domain/19-auditoria.md).

### Schema por tenant

Cada tenant tem seu próprio `flyway_schema_history` independente e suas tabelas de domínio. Conteúdo:

- Tabelas de identidade (`role`, `user_account`, `user_role`, `refresh_token`, `mfa_totp`, `webauthn_credential`, `login_attempt`, `password_history`).
- Tabelas de cadastros (`person`, `person_address`, `person_contact`, `person_modality`, `branch`, `branch_fiscal`, `employee`, catálogos).
- Tabelas de domínio (`product`, `invoice`, `stock_movement`, `financial_record`, etc).
- Tabela do Modulith (`event_publication`).

## Resolução de tenant em runtime

### Fluxo de uma requisição autenticada

1. Request chega com `Authorization: Bearer <jwt>`.
2. `JwtAuthenticationFilter` valida assinatura e expiração.
3. Extrai `tid` (tenant ID) e `tslug` (tenant slug) das claims.
4. **`TenantContext.set(tenantId, schemaName)`** ← armazenado em ThreadLocal (e também em virtual thread inheritable scope).
5. `MultiTenantConnectionProvider.getConnection(schemaName)` é invocado pelo Hibernate ao abrir conexão.
6. Connection executa `SET search_path TO tenant_demo, public;` antes de qualquer query.
7. Hibernate emite SQL normal — Postgres resolve as tabelas no schema do tenant.
8. Ao final do request: `TenantContext.clear()` no filter `afterCompletion`.

### Fluxo de uma requisição não-autenticada (login)

1. Request `POST /api/v1/auth/login` traz `tenantSlug` no body.
2. `TenantResolverFilter` lê o body (ou um header `X-Tenant-Slug`) **antes** do auth filter.
3. Resolve `slug → tenant_id, schema_name` consultando `shared.tenant`.
4. **`TenantContext.set(...)`** e segue com o login normalmente.
5. Após login bem-sucedido, JWT emitido carrega `tid` e `tslug`.

Subdomínio (`demo.example.test`) **opcional** como alternativa ao `tenantSlug` no body — `TenantResolverFilter` checa também o `Host:` header.

### Garantia: tenant nunca vem de path/query/body de endpoints autenticados

- `TenantContext.get()` é a **única** fonte da verdade após login.
- ArchUnit garante que controllers não recebem `tenantId` como `@PathVariable` ou `@RequestParam`.
- Casos legítimos de "operar em outro tenant" (super-admin de plataforma) usam endpoint dedicado em `platform/admin/...` com `tenantSlug` explícito + role `PLATFORM_ADMIN` + step-up.

## Hibernate multi-tenancy — configuração

```java
@Configuration
public class MultiTenancyConfig {

    @Bean
    MultiTenantConnectionProvider<String> multiTenantConnectionProvider(DataSource ds) {
        return new SchemaPerTenantConnectionProvider(ds);
    }

    @Bean
    CurrentTenantIdentifierResolver<String> currentTenantIdentifierResolver() {
        return new ThreadLocalTenantResolver();   // lê de TenantContext
    }

    @Bean
    HibernatePropertiesCustomizer multiTenancyCustomizer(
            MultiTenantConnectionProvider<String> connectionProvider,
            CurrentTenantIdentifierResolver<String> tenantResolver) {
        return props -> {
            props.put(AvailableSettings.MULTI_TENANT_CONNECTION_PROVIDER, connectionProvider);
            props.put(AvailableSettings.MULTI_TENANT_IDENTIFIER_RESOLVER, tenantResolver);
        };
    }
}
```

```java
public class SchemaPerTenantConnectionProvider
        implements MultiTenantConnectionProvider<String> {

    private final DataSource dataSource;

    @Override
    public Connection getAnyConnection() throws SQLException {
        Connection c = dataSource.getConnection();
        try (var st = c.createStatement()) {
            st.execute("SET search_path TO shared, public");
        }
        return c;
    }

    @Override
    public Connection getConnection(String tenantSchema) throws SQLException {
        Connection c = dataSource.getConnection();
        try (var st = c.createStatement()) {
            st.execute("SET search_path TO " + quoteSchemaName(tenantSchema) + ", shared, public");
        }
        return c;
    }

    @Override
    public void releaseConnection(String tenant, Connection c) throws SQLException {
        try (var st = c.createStatement()) {
            st.execute("RESET search_path");
        }
        c.close();
    }
    // ... releaseAnyConnection, supportsAggressiveRelease, ...
}
```

**Pool**: HikariCP único compartilhado, com `search_path` resetado no release para evitar leak entre conexões. Validar via teste de carga que reset não vira gargalo; se for, considerar pool-per-tenant.

## Provisionamento de tenant

`TenantProvisioningService` orquestra:

1. **Validações de entrada**: slug único, CNPJ válido, dados do OWNER.
2. **Transação em `shared`**: inserir registro `shared.tenant` com status `PROVISIONING`.
3. **Cria schema**: `CREATE SCHEMA "tenant_demo";`
4. **Aplica Flyway**: invoca `Flyway.configure().dataSource(...).schemas("tenant_demo").load().migrate();`
5. **Bootstrap**: seed de:
   - Roles system (`OWNER`, `MANAGER`, `VENDEDOR`, etc) com permissions associadas.
   - Catálogos default (modalidades de pessoa, segmentos, tipos de endereço/contato, unidades de medida).
   - Plano de contas básico (Ativo, Passivo, Receita, Despesa).
   - Filial matriz inicial.
   - Usuário OWNER com senha temporária (force change no primeiro login).
6. **Atualiza status** para `ACTIVE` em `shared.tenant`.
7. **Envia welcome email** para OWNER com instruções.

Falha em qualquer step: rollback do schema (`DROP SCHEMA "tenant_X" CASCADE`) e marcação de `shared.tenant` como `FAILED` para investigação. Idempotente — reprovisionar limpa estado parcial.

```java
@Service
@Transactional
public class TenantProvisioningService {
    private final TenantRepository tenants;
    private final DataSource dataSource;
    private final Flyway flywayPrototype;
    private final TenantBootstrapper bootstrapper;

    public Tenant provision(ProvisionTenantCommand cmd) {
        Tenant t = tenants.save(new Tenant(...status=PROVISIONING));
        try {
            createSchema(t.schemaName());
            applyMigrations(t.schemaName());
            bootstrapper.seed(t);
            tenants.activate(t.id());
            publish(new TenantProvisionedEvent(t.id(), t.slug(), Instant.now()));
            return t;
        } catch (Exception e) {
            log.error("provisioning failed for {}", t.slug(), e);
            dropSchemaQuietly(t.schemaName());
            tenants.markFailed(t.id(), e.getMessage());
            throw new TenantProvisioningException(t.slug(), e);
        }
    }
    // ...
}
```

## Migrations Flyway

### Estrutura

```
src/main/resources/db/migration/
├── shared/             ← migrations do schema 'shared' (executadas no boot da app)
│   ├── V1__create_tenant_table.sql
│   ├── V2__create_permission_table.sql
│   └── ...
├── audit/              ← migrations do schema 'audit' (boot da app)
│   ├── V1__create_audit_event_table.sql
│   └── ...
├── platform/           ← migrations do schema 'platform' (boot)
│   └── ...
└── tenant/             ← migrations aplicadas por tenant (no provisioning + sempre que houver tenant ativo)
    ├── V1__create_role_and_user.sql
    ├── V2__create_person_and_address.sql
    ├── V3__create_branch_and_employee.sql
    ├── V4__create_product_and_stock.sql
    ├── V5__create_invoice_and_tax.sql
    └── ...
```

### Execução

- **No boot da aplicação**: Flyway roda migrations de `shared/`, `audit/`, `platform/` automaticamente (locations configuradas em `application.yml`).
- **No provisionamento de tenant novo**: `TenantProvisioner` invoca Flyway com location `tenant/` e schema do novo tenant.
- **No upgrade de versão**: job de pós-deploy itera `shared.tenant` (status ACTIVE), invoca Flyway com location `tenant/` para cada um. Idempotente — Flyway pula migrations já aplicadas.

### Tabela `flyway_schema_history` por tenant

Cada schema de tenant tem sua própria tabela. Vantagem: tenant pode ficar uma versão atrás temporariamente (canary upgrade). Desvantagem: cada upgrade de versão é N runs de Flyway — paralelizar via thread pool.

### Restrições de migrations de `tenant/`

- **Idempotentes** quando possível (`CREATE TABLE IF NOT EXISTS` para tabelas estáveis).
- **Sem referência a schemas de outros tenants** — só `shared` e `audit` quando necessário.
- **Sem dados de seed que mudam entre tenants** — bootstrap fica em código Java (`TenantBootstrapper`), não em SQL migration.
- **DDL é review-obrigatório** — toda PR com migration passa por revisão arquitetural.

## Multi-filial dentro do tenant

Tenant agrupa **filiais** (`branch`) — unidades operacionais. Cada filial pode ter:

- CNPJ próprio (matriz) ou ser subordinada (filial).
- Endereço e regime tributário (`branch_fiscal`).
- Certificado A1 próprio se emite NFe.
- Estoque próprio (`product_stock(product_id, branch_id, ...)`).
- Sequencial fiscal próprio (`invoice_series(branch_id, series, next_number)`).

### Filial ativa na sessão

Usuário tem um conjunto de filiais permitidas (`user_branch(user_id, branch_id)`). Ao logar, escolhe ou recebe automaticamente a primeira como **filial ativa** — registrada no claim `bid` do JWT.

Trocar de filial: `POST /api/v1/auth/switch-branch` com `{ branchId }` → backend valida `user_branch` e emite novo token com `bid` atualizado.

### Operações cross-filial

Permissão dedicada `*.consultar-todas-filiais` para relatórios consolidados. Sem essa permission, queries são filtradas por `branch_id = currentBranch()`. Filtragem é responsabilidade de cada service (não há `@Filter` global de Hibernate para branch — overengineering).

## Operações administrativas cross-tenant

Endpoints em `/api/v1/platform/...` exigem role especial `PLATFORM_ADMIN` (não pertence a um tenant). Casos:

- Listar tenants, suspender, terminar.
- Aplicar migration manual em tenant específico.
- Reset de senha em escala (com aprovação).
- Anonimização em massa para LGPD cross-tenant.

Implementação: `TenantContext` setado para o tenant alvo explicitamente via header `X-Operate-As-Tenant: <tenantId>`, com auditoria reforçada (`PLATFORM_ADMIN_ACTION` event).

## Limites operacionais

| Métrica | Limite | Ação ao aproximar |
|---|---|---|
| Schemas por database | 500 | Aviso a 80% (400); plano de sharding |
| Conexões totais (pool por tenant não) | 200 simultâneas | Aviso a 150; tuning pool ou pool-per-tenant |
| Migrations pendentes em tenant | 0 | Job de upgrade falha CI se algum tenant ficou atrás |
| Tempo de provisionamento | < 30s | Alert se > 60s |
| Tempo de migration por tenant em upgrade | < 5s | Alert se algum tenant levar > 15s |

## Sharding (v2)

Quando passar 500 tenants:

1. **Multiple physical databases** (`erp-poc-shard-1`, `erp-poc-shard-2`).
2. Tabela `shared.tenant` adiciona `shard_id`.
3. `MultiTenantConnectionProvider` resolve `shard_id → DataSource` antes de setar schema.
4. Migrations rodam por shard.
5. **Sem cross-shard queries** — relatórios agregam via ETL para warehouse separado.

Decisão de sharding adiada — implementar quando volume justificar (≥400 tenants ativos).

## Testes de multi-tenancy

- **Teste de isolamento básico**: cria 2 tenants em Testcontainers, insere `person` em cada, garante que query no tenant A não retorna dados do tenant B.
- **Teste de provisionamento**: provision tenant + verifica schemas/tabelas/seed criados.
- **Teste de migration**: rodar migration nova em 2 tenants simulados, verificar idempotência.
- **Teste de `TenantContext` em virtual threads**: garantir que contexto propaga em `@Async` e `CompletableFuture`.
- **Teste ArchUnit**: nenhum controller tem `tenantId` como parâmetro de path/query/body.

## Backup, restore e portabilidade

- **Backup diário automatizado** pelo Azure Database for PostgreSQL Flexible Server + PITR via WAL contínuo (até 35 dias de retenção configurável).
- **Backup por tenant** sob demanda: `pg_dump -h ... -n tenant_demo -f demo.sql` armazenado no backend configurado.
- **Restore de tenant individual**: importar dump em schema temporário, validar, renomear.
- **Portabilidade (LGPD/comercial)**: exportar schema do tenant + payload de audit log filtrado por `tenant_id` + blobs em Azure Blob Storage (`tenants/<tenant-id>/...`).
- **Eliminação**: `DROP SCHEMA tenant_X CASCADE` + delete em `shared.tenant` + purge dos blobs Azure + purge de audit (respeitando retenção legal de 5 anos para dados fiscais, anonimização após).

## Não-funcionais

- **Tempo de resolução de tenant em request**: < 1ms (cache Caffeine de `slug → schemaName` com TTL 60s).
- **Overhead de `SET search_path`**: ~0.1ms por conexão; aceitável.
- **Migration por tenant durante upgrade**: paralelismo 8 threads default, configurável.
- **Disponibilidade do provisioning**: 99.9% (não é critical path runtime).
- **RTO** (Recovery Time Objective): 4h para restore completo; 1h para restore de tenant individual.
- **RPO** (Recovery Point Objective): 5min (WAL contínuo).

## Anti-padrões

- **Hardcoding de schema** em SQL nativo — sempre usar JPA ou named queries que respeitam `search_path`.
- **Caching cross-tenant** sem chave de tenant — qualquer cache (Caffeine, Redis) deve ter `tenantId` na key.
- **Passar `tenantId` por DTO** entre serviços — usar `TenantContext.get()`.
- **`@Transactional` que muda schema** — Flyway/provisioner usa connection dedicada, não a transaction corrente.
- **Schema do tenant aceitar conexão sem `search_path`** — defesa: revogar permissions de `public` para usuário da app em produção, exigir search_path explícito.

## Referências

- Hibernate User Guide — Multi-Tenancy
- Postgres Docs — Schemas
- ADR-001 (este documento)
