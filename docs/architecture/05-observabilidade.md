# 05 — Observabilidade

Três pilares (logs, métricas, traces) + audit log como quarto pilar dedicado a compliance. Observabilidade é **requisito de v1**, não evolução.

## Princípios

1. **Observável por construção** — toda lib core suporta Micrometer/MDC/OTel; serviços novos seguem o padrão sem decisão extra.
2. **Audit ≠ logs operacionais** — auditoria é dado de negócio (retenção 5 anos, schema próprio); logs são telemetria (retenção 30 dias).
3. **Estruturado, sempre** — JSON em logs, métricas com tags consistentes, traces com atributos padronizados.
4. **Correlation transversal** — `tenantId`, `userId`, `requestId`, `traceId` propagados em tudo.
5. **Sem PII em telemetria** — CPF/CNPJ/email mascarados; nomes só se necessário e com mascaramento parcial.
6. **Sinais primeiro, dashboards depois** — definir o que medir antes de pintar gráficos.

## Logs

### Stack

- **SLF4J** como façade.
- **Logback** como implementação.
- **logstash-logback-encoder** para JSON structured output.
- **Sink**: stdout (12-factor); coletor é o **Azure Monitor / Log Analytics Workspace** (via Container Insights no AKS), com `Application Insights` para correlação com traces.

### Configuração default (`logback-spring.xml`)

```xml
<configuration>
  <springProperty name="appName" source="spring.application.name" defaultValue="modular-erp-poc"/>
  <springProperty name="env" source="spring.profiles.active" defaultValue="local"/>

  <appender name="STDOUT_JSON" class="ch.qos.logback.core.ConsoleAppender">
    <encoder class="net.logstash.logback.encoder.LogstashEncoder">
      <customFields>{"app":"${appName}","env":"${env}"}</customFields>
      <includeMdcKeyName>tenantId</includeMdcKeyName>
      <includeMdcKeyName>tenantSlug</includeMdcKeyName>
      <includeMdcKeyName>userId</includeMdcKeyName>
      <includeMdcKeyName>sessionId</includeMdcKeyName>
      <includeMdcKeyName>requestId</includeMdcKeyName>
      <includeMdcKeyName>traceId</includeMdcKeyName>
      <includeMdcKeyName>spanId</includeMdcKeyName>
      <fieldNames>
        <timestamp>ts</timestamp>
        <message>msg</message>
        <thread>thread</thread>
        <logger>logger</logger>
        <level>level</level>
        <stackTrace>stack</stackTrace>
      </fieldNames>
      <includeContext>false</includeContext>
    </encoder>
  </appender>

  <appender name="STDOUT_PRETTY" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{HH:mm:ss.SSS} %-5level [%X{tenantSlug:-}/%X{userId:-}] %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <springProfile name="local | test">
    <root level="INFO"><appender-ref ref="STDOUT_PRETTY"/></root>
  </springProfile>

  <springProfile name="dev | staging | prod">
    <root level="INFO"><appender-ref ref="STDOUT_JSON"/></root>
    <logger name="dev.dimi.erp" level="INFO"/>
    <logger name="org.springframework.web" level="INFO"/>
    <logger name="org.hibernate.SQL" level="WARN"/>
    <logger name="org.hibernate.type.descriptor.sql.BasicBinder" level="WARN"/>
  </springProfile>
</configuration>
```

### MDC populado por filter

```java
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ObservabilityFilter implements Filter {

    public void doFilter(ServletRequest req, ServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        var http = (HttpServletRequest) req;
        var requestId = Optional.ofNullable(http.getHeader("X-Request-Id"))
            .orElseGet(() -> UuidCreator.getTimeOrderedEpoch().toString());

        ((HttpServletResponse) res).setHeader("X-Request-Id", requestId);

        MDC.put("requestId", requestId);
        // tenantId, userId vêm depois do auth filter:
        TenantContext.currentTenant().ifPresent(t -> {
            MDC.put("tenantId", t.id().toString());
            MDC.put("tenantSlug", t.slug());
        });
        SessionContext.current().ifPresent(s -> {
            MDC.put("userId", s.userId().toString());
            MDC.put("sessionId", s.sessionId().toString());
        });
        // trace/span vêm do OTel agent automaticamente (MDC injection)
        try {
            chain.doFilter(req, res);
        } finally {
            MDC.clear();
        }
    }
}
```

### Convenções de mensagem

- **Verbo no passado** descrevendo fato consumado: `"pessoa registrada"`, `"nfe autorizada"`, `"pagamento estornado"`.
- **Campos como key=value** ao final: `"pessoa registrada id={} tipo={}"`.
- **Nunca** concatenação de string (`"abc " + var`) — placeholders `{}` permitem o framework não formatar quando o nível está desabilitado.
- **PII proibida em mensagens** — IDs sim, valores sensíveis não.
- **Stack traces** apenas em `error` com exception como último argumento: `log.error("falha ao emitir nfe id={}", id, e)`.

### Níveis e usos

| Nível | Quando |
|---|---|
| `ERROR` | Falha que exige ação humana. Esperado: raro. Alertável. |
| `WARN` | Condição suspeita ou recuperável (retry, validação falha de input). |
| `INFO` | Eventos de negócio relevantes (login, cadastro, emissão, fechamento). |
| `DEBUG` | Detalhes técnicos (query lenta, decisão de roteamento). |
| `TRACE` | Apenas dev/troubleshooting pontual. |

### Retenção e amostragem

- **Retenção** padrão: 30 dias em hot storage, 90 dias em arquivo (custos da plataforma definem).
- **Sem amostragem** de `ERROR`/`WARN`.
- **Sampling de DEBUG**: 1% em produção (override por tenant para troubleshooting).

## Métricas

### Stack

- **Micrometer** como façade.
- **Prometheus** registry disponível, mas o endpoint não é exposto na configuração publicada.
- **Spring Boot Actuator** expõe somente health e info no mesmo servidor HTTP; qualquer porta interna de métricas exige configuração de deployment separada.
- **OpenTelemetry Metrics** opcional para envio direto a APM (NewRelic, Datadog) sem Prometheus intermediário.

### Métricas RED por endpoint

Spring Boot Actuator + Micrometer entrega out-of-the-box:

| Métrica | Tipo | Tags |
|---|---|---|
| `http.server.requests` (count) | Counter | `method`, `uri`, `status`, `outcome`, `exception` |
| `http.server.requests` (timer) | Timer (p50/p95/p99) | mesmas |
| `jvm.memory.used` | Gauge | `area`, `id` |
| `jvm.gc.pause` | Timer | `action`, `cause` |
| `hikaricp.connections.usage` | Timer | `pool` |
| `hibernate.queries` | Counter | `query` |

### Métricas custom por módulo

Cada bounded context expõe métricas de domínio relevantes:

```java
@Service
@RequiredArgsConstructor
public class EmitInvoiceService {
    private final MeterRegistry meters;
    private final FiscalProvider acbr;

    public InvoiceId emit(EmitInvoiceCommand cmd) {
        var timer = Timer.start(meters);
        try {
            var result = doEmit(cmd);
            timer.stop(meters.timer("fiscal.invoice.emit",
                "outcome", "success",
                "model", cmd.model().name(),
                "branchId", cmd.branchId().toString()));
            meters.counter("fiscal.invoice.emitted",
                "model", cmd.model().name()).increment();
            return result.id();
        } catch (Exception e) {
            timer.stop(meters.timer("fiscal.invoice.emit",
                "outcome", "failure",
                "model", cmd.model().name(),
                "reason", e.getClass().getSimpleName()));
            throw e;
        }
    }
}
```

### Catálogo de métricas custom por módulo (v1)

| Módulo | Métrica | Descrição |
|---|---|---|
| **identidade** | `auth.login.attempts` | Counter de tentativas (tags: outcome, reason) |
| | `auth.login.duration` | Timer do fluxo completo |
| | `auth.mfa.challenges` | Counter de MFA challenges (tags: method) |
| | `auth.refresh.token.rotations` | Counter de rotações |
| | `auth.session.active` | Gauge de sessões ativas (por tenant) |
| **cadastros** | `pessoa.registered` | Counter por tipo (PF/PJ) |
| | `pessoa.anonymized` | Counter LGPD |
| **estoque** | `stock.movement.count` | Counter por sentido (INBOUND/OUTBOUND) |
| | `stock.snapshot.lock.duration` | Timer do pessimistic lock |
| | `stock.insufficient.errors` | Counter de saldo insuficiente |
| **fiscal** | `fiscal.invoice.emit` | Timer + counter (outcome, model) |
| | `fiscal.invoice.cancel` | Timer + counter |
| | `fiscal.acbr.calls` | Counter por endpoint ACBr |
| | `fiscal.acbr.errors` | Counter por tipo de erro |
| | `fiscal.dfe.sync.events` | Counter de eventos DFe processados |
| **financeiro** | `mvf.created` | Counter (tags: direction, source) |
| | `payment.processed` | Counter |
| | `payment.reversed` | Counter |
| **modulith** | `event.publication.created` | Counter de eventos publicados |
| | `event.publication.completed` | Counter de listeners ok |
| | `event.publication.failed` | Counter de listeners com falha |
| | `event.publication.pending.age` | Gauge — idade do mais antigo pendente |

### Tags obrigatórias

Todas as métricas custom devem ter, no mínimo:

- `tenantId` — quando aplicável (operações por tenant). Cuidado: alta cardinalidade. Considerar agregar por `tenantPlan` se tenants > 100.
- `module` — `identidade`, `fiscal`, etc.
- `outcome` — `success`, `failure` (timers).

### SLOs e SLIs

| Indicador | SLI | SLO |
|---|---|---|
| Disponibilidade API geral | `http.server.requests` `outcome=success` / total | 99.9% mensal |
| Latência POST `/api/v1/invoices` p95 | timer `fiscal.invoice.emit` p95 | < 3s |
| Latência POST `/auth/login` p95 | timer `auth.login.duration` p95 | < 800ms |
| Disponibilidade `/auth/*` | success rate | 99.95% mensal |
| Idade do evento outbox mais antigo | `event.publication.pending.age` | < 5min |

Burn rate alerts (fast 2%/1h e slow 5%/6h) configurados no Prometheus.

### Painéis Grafana mínimos

1. **Overview** — RPS, p95 latência, erro rate, conexões DB, heap, GC, virtual threads ativas.
2. **Por endpoint** — top 20 por RPS e por latência.
3. **Por tenant** — top 20 tenants por carga.
4. **Auth** — login attempts, MFA, lockouts, refresh churn.
5. **Fiscal** — emissões/min, latência ACBr, falhas por motivo.
6. **Financeiro** — MVF criadas, pagamentos, estornos.
7. **Modulith eventos** — backlog, age do mais antigo, taxa de processamento.
8. **Infra** — Postgres connections, slow queries, table size por tenant.

JSON dos painéis versionados em `infra/grafana/dashboards/`.

## Traces

### Stack

- **OpenTelemetry Java Agent** anexado no Dockerfile (auto-instrumentation Spring, JDBC, OkHttp, Modulith eventos).
- **Exporter OTLP** para **Azure Monitor / Application Insights** via `azure-monitor-opentelemetry-exporter` ou OTLP nativo. Configurável por env.
- **Sampling** 10% em produção (`OTEL_TRACES_SAMPLER=parentbased_traceidratio`, `OTEL_TRACES_SAMPLER_ARG=0.1`).
- **Always sample** quando header `X-Trace-Force=1` (debug operacional).

### Atributos padronizados

Span atributos enriquecidos via filter/interceptor:

```
tenant.id          = <uuid>
tenant.slug        = "demo"
user.id            = <uuid>
session.id         = <uuid>
http.request.id    = <uuid>           ← mesmo que MDC requestId
erp-poc.module     = "fiscal"
erp-poc.operation  = "invoice.emit"
erp-poc.aggregate.type = "invoice"
erp-poc.aggregate.id   = <uuid>
```

### Spans manuais quando OTel não vê

Operações pesadas dentro de um service (algoritmo, processamento batch) ganham span manual:

```java
@Autowired Tracer tracer;

public ReconciliationResult run(...) {
    Span span = tracer.spanBuilder("financeiro.reconciliation.run")
        .setAttribute("erp-poc.batch.size", items.size())
        .startSpan();
    try (var scope = span.makeCurrent()) {
        // trabalho pesado
        return result;
    } catch (Exception e) {
        span.recordException(e);
        span.setStatus(StatusCode.ERROR);
        throw e;
    } finally {
        span.end();
    }
}
```

### Propagação

- **Inbound**: `traceparent` header (W3C Trace Context) lido pelo OTel agent.
- **Outbound HTTP**: OkHttp instrumentation injeta `traceparent` automaticamente em chamadas para ACBr/SMTP/S3.
- **Outbound DB**: spans Postgres incluem `db.statement` (queries pesadas viram visíveis).
- **Eventos Modulith**: trace context propagado via Modulith integration (ver doc Spring Modulith Observability).

## Audit log

Pilar **separado** de logs operacionais. Audit log é **dado de negócio com retenção legal**.

### Princípios

- **Append-only** — nunca `UPDATE`/`DELETE` (apenas purge por retenção).
- **Schema próprio** (`audit`) compartilhado entre tenants, particionado por mês.
- **Imutável após gravação** — `REVOKE UPDATE, DELETE ON audit.audit_event FROM app_role`.
- **Retenção 5 anos** para eventos fiscais/financeiros; 1 ano para acesso/leitura; 90 dias para tentativas de login.
- **Anonimização LGPD** depois da retenção: payload é purgado, mas linha (com hash) permanece para integridade.

### Modelo

```sql
CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE audit.audit_event (
    id              uuid NOT NULL,
    tenant_id       uuid NOT NULL,
    user_id         uuid,
    session_id      uuid,
    occurred_at     timestamptz NOT NULL,

    event_type      text NOT NULL,            -- 'INVOICE_AUTHORIZED'
    event_category  text NOT NULL,            -- 'FISCAL', 'AUTH', 'FINANCIAL', 'LGPD', 'ADMIN'
    severity        text NOT NULL DEFAULT 'INFO' CHECK (severity IN ('INFO','WARN','CRITICAL')),

    aggregate_type  text,                     -- 'invoice'
    aggregate_id    text,                     -- pode ser composta (não só uuid)

    actor_ip        inet,
    actor_user_agent text,
    request_id      uuid,
    trace_id        text,

    summary         text,                     -- frase humana: "NFe 12345 série 001 autorizada"
    payload         jsonb,                    -- snapshot opcional (before/after)

    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- partições mensais criadas por job (pg_partman ou job dedicado):
CREATE TABLE audit.audit_event_2026_05 PARTITION OF audit.audit_event
    FOR VALUES FROM ('2026-05-01') TO ('2026-06-01');

CREATE INDEX idx_audit_event_tenant_time ON audit.audit_event(tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_event_user_time ON audit.audit_event(tenant_id, user_id, occurred_at DESC);
CREATE INDEX idx_audit_event_aggregate ON audit.audit_event(tenant_id, aggregate_type, aggregate_id);
CREATE INDEX idx_audit_event_category ON audit.audit_event(tenant_id, event_category, occurred_at DESC);
CREATE INDEX idx_audit_event_payload_gin ON audit.audit_event USING GIN (payload jsonb_path_ops);
```

### Como módulos emitem audit

Via evento de domínio + listener no módulo `auditoria`:

```java
// Em fiscal/internal/application:
public class EmitInvoiceService {
    @Transactional
    public InvoiceId emit(EmitInvoiceCommand cmd) {
        var invoice = doEmit(cmd);
        events.publishEvent(new InvoiceAuthorizedEvent(
            invoice.id(), TenantContext.tenantId(), invoice.number(),
            invoice.totalAmount(), Instant.now()
        ));
        return invoice.id();
    }
}

// Em auditoria/internal/listener:
@Component
class FiscalAuditListener {
    @ApplicationModuleListener
    void on(InvoiceAuthorizedEvent e) {
        auditWriter.write(AuditEvent.builder()
            .tenantId(e.tenantId())
            .userId(SessionContext.userId())
            .occurredAt(e.occurredAt())
            .eventType("INVOICE_AUTHORIZED")
            .eventCategory("FISCAL")
            .aggregateType("invoice")
            .aggregateId(e.invoiceId().value().toString())
            .summary("NFe %s autorizada por R$ %s".formatted(
                e.number(), e.totalAmount()))
            .payload(Map.of("invoiceId", e.invoiceId(), "number", e.number()))
            .build());
    }
}
```

`AuditWriter` é o único componente que escreve no schema `audit`. Outros módulos **nunca** acessam audit diretamente.

### Catálogo de eventos auditáveis

| Categoria | Eventos |
|---|---|
| **AUTH** | `LOGIN_SUCCESS`, `LOGIN_FAILED`, `LOGOUT`, `MFA_ENROLLED`, `MFA_RESET`, `MFA_DISABLED`, `PASSWORD_CHANGED`, `PASSWORD_RESET`, `STEP_UP_REQUIRED`, `STEP_UP_SUCCEEDED`, `SESSION_REVOKED_ALL`. |
| **ADMIN** | `USER_CREATED`, `USER_DEACTIVATED`, `USER_REACTIVATED`, `ROLE_CREATED`, `ROLE_EDITED`, `ROLE_DELETED`, `ROLE_ASSIGNED`, `ROLE_REVOKED`, `PERMISSION_GRANTED`, `PERMISSION_REVOKED`, `TENANT_CONFIGURED`, `BRANCH_CREATED`, `BRANCH_FISCAL_UPDATED`. |
| **FISCAL** | `INVOICE_AUTHORIZED`, `INVOICE_CANCELLED`, `INVOICE_CCE_ISSUED`, `INVOICE_REJECTED`, `INVOICE_INUTILIZED`, `DFE_MANIFESTED`, `CERTIFICATE_UPLOADED`, `CERTIFICATE_REPLACED`, `SERIES_INUTILIZATION`. |
| **FINANCIAL** | `MVF_CREATED`, `MVF_CANCELLED`, `MVF_RENEGOTIATED`, `PAYMENT_PROCESSED`, `PAYMENT_REVERSED`, `PERIOD_FROZEN`, `PERIOD_UNFROZEN`, `CHART_OF_ACCOUNTS_EDITED`. |
| **STOCK** | `STOCK_ADJUSTED`, `STOCK_TRANSFERRED`, `STOCK_INVENTORIED`. |
| **LGPD** | `PERSON_ANONYMIZED`, `PERSONAL_DATA_EXPORTED`, `CONSENT_GIVEN`, `CONSENT_REVOKED`. |
| **PLATFORM_ADMIN** | `TENANT_PROVISIONED`, `TENANT_SUSPENDED`, `TENANT_TERMINATED`, `IMPERSONATION_STARTED`, `IMPERSONATION_ENDED`. |

### Consultas auditoria

Endpoint admin `/api/v1/auditoria/events` (permission `audit.consultar`):

- Filtros: `eventCategory`, `eventType`, `aggregateType`, `aggregateId`, `userId`, `from`, `to`.
- Paginação cursor-based (mesma convenção dos demais endpoints — ver [08-api.md](08-api.md)).
- Resposta resumida + endpoint detail (`/auditoria/events/{id}`) para payload completo.

### Retenção e purga

Job diário em `platform.shedlock` rodando `AuditRetentionService`:

1. Para cada categoria, identificar partições mensais a purgar (passado da janela de retenção).
2. **Eventos fiscais/financeiros**: anonimizar `payload` (substituir por `{"anonymized": true}`), manter linha por 10 anos.
3. **Eventos AUTH/LGPD**: purgar payload depois de 1 ano; linha (com hash do payload) por 5 anos.
4. **Login attempts**: drop de partição após 90 dias.
5. **Tudo registrado** em meta-evento `AUDIT_RETENTION_APPLIED`.

## Outbox monitoring

Spring Modulith expõe `event_publication` automaticamente:

- Endpoint `/actuator/modulith/events` lista publicações pendentes.
- Métricas `event.publication.*` (ver acima).
- Job `IncompleteEventRetryJob` retenta publicações pendentes a cada 1 minuto (cap em 10 retentativas, depois alerta).

## Alertas

### Severidade

- **P1 (page imediato)**: SEFAZ down impactando emissão > 5min; cluster degradado (>1 instância em CrashLoop); error rate > 5%; reuso de refresh token detectado; falha de pagamento batch.
- **P2 (Slack)**: latência p95 > SLO por 10min; backlog de outbox > 100; certificado A1 vence em 7 dias; failed login > 50/min em mesmo IP.
- **P3 (email)**: certificado vence em 30 dias; tenant > 80% do limite do plano; database connection pool > 70%.

### Configuração

Alertmanager + PagerDuty (oncall) + Slack (#ops, #fiscal, #sec-alerts) + email. Configurações YAML versionadas em `infra/alerting/`.

### Catálogo de alertas mínimo (v1)

| Alerta | Condição | Severidade |
|---|---|---|
| `HighErrorRate` | `http.server.requests outcome=server_error / total > 1%` por 5min | P1 |
| `AuthLoginUnavailable` | `/auth/login outcome=success / total < 99%` por 5min | P1 |
| `RefreshTokenReuse` | `auth.refresh.reuse.detected` > 0 | P1 |
| `ACBrUnavailable` | `fiscal.acbr.errors / fiscal.acbr.calls > 50%` por 5min | P1 |
| `OutboxBacklog` | `event.publication.pending.age > 5min` | P2 |
| `HighLatencyP95` | `http.server.requests p95 > SLO` por 10min | P2 |
| `LoginLockoutWave` | `auth.login.lockouts > 10/min` | P2 |
| `CertificateExpiringSoon` | `fiscal.certificate.days_until_expiry < 7` | P2 |
| `DBConnectionPoolHigh` | `hikaricp.connections.usage > 70%` por 10min | P3 |
| `TenantQuotaApproaching` | `tenant.quota.usage > 80%` | P3 |

## Privacidade em telemetria

- **MDC e atributos de span** carregam IDs, **nunca** valores sensíveis.
- **Mascaramento** quando inevitável: `cpf=***.456.***-**` ou `email=jo***@***ompany.com`.
- **OpenTelemetry processor** customizado filtra atributos sensíveis antes de exportar (lista negra: `password`, `token`, `secret`, `cpf`, `cnpj` raw).
- **Logs proibidos**: corpo de request com PII, JWT completo, refresh token, certificado.

## Custo e cardinalidade

- **Tags com alta cardinalidade** (tenant_id quando > 100 tenants) — agregar por dimensão estática (`plan`, `region`) ou amostrar.
- **Logs verbosos** (Hibernate SQL em DEBUG): habilitar apenas por troubleshooting específico, via override de logger via `/actuator/loggers`.
- **Traces 10% sampling** mantém custo controlado para SaaS APMs.

## Health checks

Endpoints habilitados na configuração base:

- `/actuator/health` — composição dos health indicators.
  - **liveness**: app responde (não em deadlock).
  - **readiness**: app pronta para receber tráfego (DB conectado, migrations OK, certificados ACBr alcançáveis em probe leve).
- `/actuator/info` — versão, commit SHA, build time.

`prometheus`, `loggers`, `modulith/events`, `threaddump` e `heapdump` não são expostos. Caso uma implantação precise deles, deve usar uma interface de administração isolada e autenticação própria.

Kubernetes:

```yaml
livenessProbe:
  httpGet: { path: /actuator/health/liveness, port: 9090 }
  initialDelaySeconds: 30
  periodSeconds: 10
readinessProbe:
  httpGet: { path: /actuator/health/readiness, port: 9090 }
  initialDelaySeconds: 10
  periodSeconds: 5
```

## Não-funcionais

- **Overhead de observabilidade**: < 5% CPU adicional, < 50MB heap.
- **Latência adicional de logs JSON**: < 1ms por request.
- **Latência adicional de tracing 10%**: imperceptível.
- **Latência adicional de audit log** (escrita async via listener): < 2ms na transação original.
- **Custo storage logs**: < $0.05/usuário/mês em SaaS típico (Loki self-hosted ou similar).

## Anti-padrões a evitar

- **`println` ou `e.printStackTrace`** — viola JSON structured.
- **Tag em métrica com valor único** (e.g., `requestId`) — explosão de cardinalidade.
- **Log de PII** — vazamento legal LGPD.
- **Log de stack trace em `WARN`** — degrada sinal.
- **Span manual fora de operação relevante** — ruído.
- **`/actuator/*` exposto na porta pública** — risco de exposição.
- **Métrica criada toda request** (e.g., `meters.counter(...)` em hot path com tags dinâmicas) — recriação de bucket; cachear `Counter`/`Timer` em campos `final`.
- **Audit escrito síncrono no fluxo crítico** — preferir evento + listener (transação separada).

## Roadmap pós-v1

- **v1.1**: distributed tracing entre ACBr e app via OTel; alertas customizados em Azure Monitor; Workbooks Azure para visões de tenant.
- **v1.2**: anomaly detection em métricas (Azure Application Insights Smart Detection ou ML custom). Audit log search UI.
- **v2**: BYOK para logs sensíveis. Long-term cold storage para audit em Azure Blob Storage Archive Tier.
