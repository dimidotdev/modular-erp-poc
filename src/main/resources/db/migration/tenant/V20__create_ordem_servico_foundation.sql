-- Migração aplicada POR TENANT.
-- Slice 9.1 — Ordem de Serviço (OS) foundation.
-- Apenas workflow + service_order CRUD + status history. SEM itens, SEM billing, SEM saga cancel.
--
-- O QUE NÃO ESTÁ AQUI (deferido para slice 9.2 grandão):
-- - service_order_product / service_order_service / service_order_labor (itens)
-- - service_order_billing (NFe + NFSe Saga)
-- - integração estoque/fiscal/financeiro (cross-module via eventos)
-- - cancel Saga com estorno de NFe+MVF+estoque
--
-- Doc: docs/domain/16-ordem-servico.md.

-- =============================================================================
-- 1) os_workflow_status — estados configuráveis por tenant
-- =============================================================================
CREATE TABLE os_workflow_status (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code           text NOT NULL,
    display_name   text NOT NULL,
    is_initial     boolean NOT NULL DEFAULT false,
    is_terminal    boolean NOT NULL DEFAULT false,
    is_billable    boolean NOT NULL DEFAULT false,
    is_system      boolean NOT NULL DEFAULT false,
    sort_order     int NOT NULL DEFAULT 0,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_os_workflow_status_code UNIQUE (code)
);

-- Status do sistema — `is_system=true` não pode ser deletado.
-- BILLED/CLOSED ficam pré-seedados aqui mas só são alcançáveis quando 9.2 entrar com billing.
INSERT INTO os_workflow_status (code, display_name, is_initial, is_terminal, is_billable, is_system, sort_order) VALUES
    ('OPEN',             'Aberta',           true,  false, false, true, 10),
    ('IN_PROGRESS',      'Em execução',      false, false, false, true, 20),
    ('AWAITING_PARTS',   'Aguardando peças', false, false, false, true, 30),
    ('READY',            'Pronta',           false, false, true,  true, 40),
    ('BILLED',           'Faturada',         false, false, false, true, 50),
    ('CLOSED',           'Fechada',          false, true,  false, true, 60),
    ('CANCELLED',        'Cancelada',        false, true,  false, true, 70);

-- =============================================================================
-- 2) os_workflow_transition — grafo de transições permitidas
-- =============================================================================
CREATE TABLE os_workflow_transition (
    from_status_id        uuid NOT NULL REFERENCES os_workflow_status(id) ON DELETE CASCADE,
    to_status_id          uuid NOT NULL REFERENCES os_workflow_status(id) ON DELETE CASCADE,
    required_permission   text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (from_status_id, to_status_id),
    CONSTRAINT ck_os_workflow_transition_distinct CHECK (from_status_id <> to_status_id)
);

-- Transições default cobrindo o ciclo de vida normal.
-- READY→BILLED só funciona efetivamente quando billing entra em 9.2; o grafo já existe.
INSERT INTO os_workflow_transition (from_status_id, to_status_id, required_permission) VALUES
    ((SELECT id FROM os_workflow_status WHERE code = 'OPEN'),           (SELECT id FROM os_workflow_status WHERE code = 'IN_PROGRESS'),     NULL),
    ((SELECT id FROM os_workflow_status WHERE code = 'OPEN'),           (SELECT id FROM os_workflow_status WHERE code = 'AWAITING_PARTS'),  NULL),
    ((SELECT id FROM os_workflow_status WHERE code = 'OPEN'),           (SELECT id FROM os_workflow_status WHERE code = 'CANCELLED'),       'os.cancelar'),
    ((SELECT id FROM os_workflow_status WHERE code = 'IN_PROGRESS'),    (SELECT id FROM os_workflow_status WHERE code = 'AWAITING_PARTS'),  NULL),
    ((SELECT id FROM os_workflow_status WHERE code = 'IN_PROGRESS'),    (SELECT id FROM os_workflow_status WHERE code = 'READY'),           NULL),
    ((SELECT id FROM os_workflow_status WHERE code = 'IN_PROGRESS'),    (SELECT id FROM os_workflow_status WHERE code = 'CANCELLED'),       'os.cancelar'),
    ((SELECT id FROM os_workflow_status WHERE code = 'AWAITING_PARTS'), (SELECT id FROM os_workflow_status WHERE code = 'IN_PROGRESS'),     NULL),
    ((SELECT id FROM os_workflow_status WHERE code = 'AWAITING_PARTS'), (SELECT id FROM os_workflow_status WHERE code = 'CANCELLED'),       'os.cancelar'),
    ((SELECT id FROM os_workflow_status WHERE code = 'READY'),          (SELECT id FROM os_workflow_status WHERE code = 'BILLED'),          'os.faturar'),
    ((SELECT id FROM os_workflow_status WHERE code = 'READY'),          (SELECT id FROM os_workflow_status WHERE code = 'CANCELLED'),       'os.cancelar'),
    ((SELECT id FROM os_workflow_status WHERE code = 'BILLED'),         (SELECT id FROM os_workflow_status WHERE code = 'CLOSED'),          'os.fechar');

-- =============================================================================
-- 3) service_order — root da agregação
-- =============================================================================
CREATE TABLE service_order (
    id                       uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                  bigint NOT NULL DEFAULT 0,
    number                   bigint NOT NULL,
    branch_id                uuid NOT NULL REFERENCES branch(id),
    customer_id              uuid NOT NULL REFERENCES person(id),
    contact_id               uuid REFERENCES person_contact(id),
    equipment_description    text,
    serial_number            text,
    problem_reported         text,
    diagnosis                text,
    solution                 text,
    status_id                uuid NOT NULL REFERENCES os_workflow_status(id),
    priority                 text NOT NULL DEFAULT 'NORMAL' CHECK (priority IN ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
    opened_at                timestamptz NOT NULL DEFAULT now(),
    started_at               timestamptz,
    finished_at              timestamptz,
    billed_at                timestamptz,
    closed_at                timestamptz,
    cancelled_at             timestamptz,
    cancellation_reason      text,
    payment_condition_id     uuid REFERENCES catalog_payment_condition(id),
    total_products           numeric(15, 2) NOT NULL DEFAULT 0,
    total_services           numeric(15, 2) NOT NULL DEFAULT 0,
    total_discount           numeric(15, 2) NOT NULL DEFAULT 0,
    total                    numeric(15, 2) NOT NULL DEFAULT 0,
    notes                    text,
    created_by               uuid NOT NULL,
    assigned_to              uuid,
    created_at               timestamptz NOT NULL DEFAULT now(),
    updated_at               timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_service_order_branch_number UNIQUE (branch_id, number),
    CONSTRAINT ck_service_order_number_positive CHECK (number > 0)
);

CREATE INDEX idx_service_order_status ON service_order(status_id) WHERE closed_at IS NULL;
CREATE INDEX idx_service_order_assigned ON service_order(assigned_to) WHERE closed_at IS NULL;
CREATE INDEX idx_service_order_customer ON service_order(customer_id);
CREATE INDEX idx_service_order_branch_opened ON service_order(branch_id, opened_at DESC);

-- =============================================================================
-- 4) service_order_status_history — log append-only de transições
-- =============================================================================
CREATE TABLE service_order_status_history (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    os_id           uuid NOT NULL REFERENCES service_order(id) ON DELETE CASCADE,
    from_status_id  uuid REFERENCES os_workflow_status(id),
    to_status_id    uuid NOT NULL REFERENCES os_workflow_status(id),
    changed_at      timestamptz NOT NULL DEFAULT now(),
    changed_by      uuid NOT NULL,
    notes           text
);

CREATE INDEX idx_os_status_history_os ON service_order_status_history(os_id, changed_at DESC);
