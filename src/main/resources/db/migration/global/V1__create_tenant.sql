-- shared.tenant — catálogo central de tenants registrados na plataforma.
-- Único lugar onde tabelas de tenants são listadas; resolução slug→schema_name
-- vem daqui durante o login e o filter de tenant.

CREATE TABLE shared.tenant (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    slug            text NOT NULL UNIQUE,
    schema_name     text NOT NULL UNIQUE,
    display_name    text NOT NULL,
    legal_name      text NOT NULL,
    cnpj            char(14) UNIQUE,
    status          text NOT NULL CHECK (status IN ('PROVISIONING','ACTIVE','SUSPENDED','TERMINATED','FAILED')),
    plan            text NOT NULL DEFAULT 'BASIC',
    config          jsonb NOT NULL DEFAULT '{}'::jsonb,
    provisioned_at  timestamptz,
    suspended_at    timestamptz,
    terminated_at   timestamptz,
    failure_reason  text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_tenant_slug_format
        CHECK (slug ~ '^[a-z][a-z0-9-]{1,62}[a-z0-9]$'),
    CONSTRAINT ck_tenant_schema_name_format
        CHECK (schema_name ~ '^tenant_[a-z0-9_]+$')
);

CREATE INDEX idx_tenant_status ON shared.tenant(status);
CREATE INDEX idx_tenant_slug_active ON shared.tenant(slug) WHERE status = 'ACTIVE';

COMMENT ON TABLE shared.tenant IS
    'Catálogo central de tenants da plataforma. Toda resolução tenant ocorre aqui.';
COMMENT ON COLUMN shared.tenant.slug IS
    'Identificador URL-friendly do tenant (kebab-case, 3-64 chars).';
COMMENT ON COLUMN shared.tenant.schema_name IS
    'Schema Postgres dedicado a este tenant (formato tenant_<slug>).';
COMMENT ON COLUMN shared.tenant.status IS
    'PROVISIONING enquanto cria. ACTIVE em uso. SUSPENDED bloqueado. TERMINATED encerrado. FAILED erro provisionamento.';
