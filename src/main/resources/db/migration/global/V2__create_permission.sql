-- shared.permission — catálogo GLOBAL de permissões de domínio.
-- Permissions são imutáveis no nível tenant: o catálogo é gerido pela plataforma.
-- Tenants criam ROLES que combinam essas permissions.
--
-- Naming: dot-notation hierárquica (recurso.acao). Operações sensíveis marcadas
-- com is_sensitive=true exigem step-up auth (futuro v1.1; v1 apenas registra).

CREATE TABLE shared.permission (
    code            text PRIMARY KEY,
    display_name    text NOT NULL,
    description     text,
    category        text NOT NULL,
    is_sensitive    boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_permission_code_format
        CHECK (code ~ '^[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+$')
);

CREATE INDEX idx_permission_category ON shared.permission(category);
CREATE INDEX idx_permission_sensitive ON shared.permission(is_sensitive) WHERE is_sensitive = true;

COMMENT ON TABLE shared.permission IS
    'Catálogo global de permissões. Tenants combinam essas em roles.';
COMMENT ON COLUMN shared.permission.code IS
    'Permission code dot-notation (ex: nfe.emitir, venda.cancelar).';
COMMENT ON COLUMN shared.permission.is_sensitive IS
    'Quando true, exige step-up auth recente (5min). v1: anota; v1.1+: enforce.';
