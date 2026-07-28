-- audit.audit_event — log append-only de eventos auditáveis cross-tenant.
-- Doc: docs/domain/19-auditoria.md + docs/architecture/05-observabilidade.md.
--
-- Particionado por mês (RANGE em occurred_at). Job futuro cria partições
-- antecipadas e dropa partições além da política de retenção.
-- Permissões: somente INSERT/SELECT pra app_role (UPDATE/DELETE revogados
-- explicitamente em migration futura quando o app_role for criado).

CREATE TABLE audit.audit_event (
    id              uuid NOT NULL DEFAULT gen_random_uuid(),
    tenant_id       uuid NOT NULL,
    user_id         uuid,
    session_id      uuid,
    occurred_at     timestamptz NOT NULL,

    event_type      text NOT NULL,
    event_category  text NOT NULL CHECK (event_category IN
        ('AUTH','ADMIN','FISCAL','FINANCIAL','STOCK','LGPD','PLATFORM_ADMIN','OS','CONTRACT','SYSTEM')),
    severity        text NOT NULL DEFAULT 'INFO' CHECK (severity IN ('INFO','WARN','CRITICAL')),

    aggregate_type  text,
    aggregate_id    text,

    actor_ip        inet,
    actor_user_agent text,
    request_id      uuid,
    trace_id        text,
    correlation_id  uuid,

    summary         text NOT NULL,
    payload         jsonb,

    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

-- Partições do mês corrente e dos próximos 2 meses. Job futuro mantém
-- janela rolante; aqui criamos o suficiente para boot inicial.
DO $$
DECLARE
    start_month date := date_trunc('month', current_date)::date;
    part_name text;
    part_start date;
    part_end date;
    i int;
BEGIN
    FOR i IN 0..2 LOOP
        part_start := start_month + (i || ' month')::interval;
        part_end := part_start + interval '1 month';
        part_name := 'audit_event_' || to_char(part_start, 'YYYY_MM');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS audit.%I PARTITION OF audit.audit_event FOR VALUES FROM (%L) TO (%L)',
            part_name, part_start, part_end
        );
    END LOOP;
END $$;

CREATE INDEX idx_audit_tenant_time   ON audit.audit_event(tenant_id, occurred_at DESC);
CREATE INDEX idx_audit_user_time     ON audit.audit_event(tenant_id, user_id, occurred_at DESC);
CREATE INDEX idx_audit_aggregate     ON audit.audit_event(tenant_id, aggregate_type, aggregate_id);
CREATE INDEX idx_audit_category_time ON audit.audit_event(tenant_id, event_category, occurred_at DESC);
CREATE INDEX idx_audit_correlation   ON audit.audit_event(correlation_id) WHERE correlation_id IS NOT NULL;

COMMENT ON TABLE audit.audit_event IS
    'Audit log append-only particionado por mês. Detalhes em docs/domain/19-auditoria.md.';
COMMENT ON COLUMN audit.audit_event.correlation_id IS
    'Agrupa eventos de uma mesma Saga (ex: cancelamento de venda gera N eventos com mesmo correlation_id).';
