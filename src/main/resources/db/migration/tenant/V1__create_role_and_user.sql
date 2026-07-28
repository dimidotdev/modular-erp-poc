-- Migration aplicada POR TENANT pelo TenantProvisioningService.
-- search_path setado dinamicamente pelo Flyway para o schema do tenant
-- (ex: tenant_demo), portanto referências sem prefixo de schema apontam
-- automaticamente para tabelas do próprio tenant.
--
-- Referências cruzadas para shared.permission(code) são feitas APENAS pelo
-- valor string em role_permission.permission_code (sem FK formal porque FK
-- cross-schema entre tenant/* e shared complica Flyway).
--
-- Doc: docs/domain/10-identidade-acesso.md.

-- =====================================================================
-- role
-- =====================================================================
CREATE TABLE role (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text NOT NULL UNIQUE,
    display_name    text NOT NULL,
    description     text,
    is_system       boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_role_code_format
        CHECK (code ~ '^[A-Z][A-Z0-9_]+$')
);

CREATE INDEX idx_role_system ON role(is_system);

COMMENT ON TABLE role IS
    'Roles do tenant: agrupa permissions. system roles (OWNER/MANAGER/...) seed no provisionamento, imutáveis.';

-- =====================================================================
-- role_permission
-- =====================================================================
CREATE TABLE role_permission (
    role_id         uuid NOT NULL REFERENCES role(id) ON DELETE CASCADE,
    permission_code text NOT NULL,
    granted_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_code)
);

CREATE INDEX idx_role_permission_code ON role_permission(permission_code);

COMMENT ON TABLE role_permission IS
    'Permissions atribuídas a cada role. permission_code referencia shared.permission(code) sem FK formal (cross-schema).';

-- =====================================================================
-- user_account
-- =====================================================================
CREATE TABLE user_account (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                     bigint NOT NULL DEFAULT 0,
    username                    text NOT NULL,
    email                       text NOT NULL,
    full_name                   text NOT NULL,
    password_hash               text NOT NULL,
    status                      text NOT NULL CHECK (status IN ('ACTIVE','SUSPENDED','DEACTIVATED')),
    must_change_password        boolean NOT NULL DEFAULT false,
    last_login_at               timestamptz,
    last_login_ip               inet,
    person_id                   uuid,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    deactivated_at              timestamptz,
    deactivated_reason          text
);

CREATE UNIQUE INDEX uq_user_username_lower ON user_account (LOWER(username));
CREATE UNIQUE INDEX uq_user_email_lower    ON user_account (LOWER(email));
CREATE INDEX idx_user_status ON user_account(status);
CREATE INDEX idx_user_person ON user_account(person_id) WHERE person_id IS NOT NULL;

COMMENT ON TABLE user_account IS
    'Usuários do tenant. password_hash em Argon2id. person_id referência soft para cadastros.person (FK não enforced em DB).';

-- =====================================================================
-- user_role
-- =====================================================================
CREATE TABLE user_role (
    user_id     uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    role_id     uuid NOT NULL REFERENCES role(id),
    assigned_at timestamptz NOT NULL DEFAULT now(),
    assigned_by uuid REFERENCES user_account(id),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_role_role ON user_role(role_id);

-- =====================================================================
-- refresh_token
-- =====================================================================
CREATE TABLE refresh_token (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    session_id      uuid NOT NULL,
    chain_id        uuid NOT NULL,
    token_hash      text NOT NULL UNIQUE,
    issued_at       timestamptz NOT NULL DEFAULT now(),
    expires_at      timestamptz NOT NULL,
    used_at         timestamptz,
    revoked_at      timestamptz,
    revoked_reason  text,
    user_agent      text,
    ip              inet
);

CREATE INDEX idx_refresh_user_active   ON refresh_token(user_id) WHERE revoked_at IS NULL;
CREATE INDEX idx_refresh_chain         ON refresh_token(chain_id);
CREATE INDEX idx_refresh_session       ON refresh_token(session_id);
CREATE INDEX idx_refresh_expires_active ON refresh_token(expires_at) WHERE revoked_at IS NULL;

COMMENT ON TABLE refresh_token IS
    'Refresh tokens com rotação one-shot. chain_id agrupa toda a chain da sessão; uso de token já marcado em used_at revoga toda a chain.';

-- =====================================================================
-- login_attempt — particionada por dia (retenção 90 dias)
-- =====================================================================
CREATE TABLE login_attempt (
    id              uuid NOT NULL DEFAULT gen_random_uuid(),
    user_id         uuid,
    username        text NOT NULL,
    ip              inet NOT NULL,
    success         boolean NOT NULL,
    failure_reason  text,
    attempted_at    timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (id, attempted_at)
) PARTITION BY RANGE (attempted_at);

-- Partições dos últimos 1 dia + próximos 7 dias para boot inicial.
-- Job futuro cria janela rolante.
DO $$
DECLARE
    base_day date := current_date - 1;
    part_name text;
    part_start date;
    part_end date;
    i int;
BEGIN
    FOR i IN 0..7 LOOP
        part_start := base_day + i;
        part_end := part_start + 1;
        part_name := 'login_attempt_' || to_char(part_start, 'YYYY_MM_DD');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS %I PARTITION OF login_attempt FOR VALUES FROM (%L) TO (%L)',
            part_name, part_start, part_end
        );
    END LOOP;
END $$;

CREATE INDEX idx_login_attempt_user_time ON login_attempt(username, attempted_at DESC);
CREATE INDEX idx_login_attempt_ip_time   ON login_attempt(ip, attempted_at DESC);

-- =====================================================================
-- password_history
-- =====================================================================
CREATE TABLE password_history (
    user_id         uuid NOT NULL REFERENCES user_account(id) ON DELETE CASCADE,
    password_hash   text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, created_at)
);

COMMENT ON TABLE password_history IS
    'Histórico das últimas N senhas (hash Argon2). Política: rejeitar reuso das últimas 5.';
