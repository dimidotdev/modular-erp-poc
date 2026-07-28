-- Slice 11 — Auditoria: tabelas de retenção e trilha de execução do job.
-- Doc: docs/domain/19-auditoria.md.
--
-- audit.audit_event já existe (V4) particionada por mês. V9 adiciona:
-- - retention_policy: política por categoria
-- - retention_run: trilha histórica do job
--
-- A role da aplicação deve receber apenas INSERT/SELECT em audit_event.
-- O provisionamento dessas permissões pertence à plataforma de execução.

CREATE TABLE audit.retention_policy (
    category        text PRIMARY KEY CHECK (category IN
        ('AUTH','ADMIN','FISCAL','FINANCIAL','STOCK','LGPD','PLATFORM_ADMIN','OS','CONTRACT','SYSTEM')),
    retain_days     int NOT NULL CHECK (retain_days > 0),
    action_at_eol   text NOT NULL CHECK (action_at_eol IN ('PURGE','ANONYMIZE')),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

-- Seed conforme docs/domain/19-auditoria.md tabela de retenção
INSERT INTO audit.retention_policy (category, retain_days, action_at_eol) VALUES
    ('AUTH',            90,   'PURGE'),
    ('ADMIN',           1825, 'ANONYMIZE'),
    ('FISCAL',          3650, 'ANONYMIZE'),
    ('FINANCIAL',       1825, 'ANONYMIZE'),
    ('STOCK',           1825, 'ANONYMIZE'),
    ('LGPD',            3650, 'ANONYMIZE'),
    ('PLATFORM_ADMIN',  1825, 'ANONYMIZE'),
    ('OS',              1825, 'ANONYMIZE'),
    ('CONTRACT',        1825, 'ANONYMIZE'),
    ('SYSTEM',          180,  'PURGE');

CREATE TABLE audit.retention_run (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    started_at          timestamptz NOT NULL DEFAULT now(),
    finished_at         timestamptz,
    status              text NOT NULL CHECK (status IN ('RUNNING','SUCCESS','FAILED')),
    purged_count        bigint NOT NULL DEFAULT 0,
    anonymized_count    bigint NOT NULL DEFAULT 0,
    partitions_dropped  int NOT NULL DEFAULT 0,
    partitions_created  int NOT NULL DEFAULT 0,
    error_detail        text
);
CREATE INDEX idx_audit_retention_run_started ON audit.retention_run(started_at DESC);
