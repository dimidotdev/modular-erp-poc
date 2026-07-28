-- Migração aplicada POR TENANT.
-- Slice 10.1 — Contratos foundation: contract + item + amendment + adjustment.
-- Billing cycles + job diário + listener financeiro ficam para slice 10.2.
--
-- Doc: docs/domain/18-contratos.md.

CREATE TABLE contract (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                     bigint NOT NULL DEFAULT 0,
    number                      bigint NOT NULL,
    branch_id                   uuid NOT NULL REFERENCES branch(id),
    customer_id                 uuid NOT NULL REFERENCES person(id),
    contact_id                  uuid REFERENCES person_contact(id),
    title                       text NOT NULL,
    description                 text,
    status                      text NOT NULL CHECK (status IN
        ('DRAFT','ACTIVE','SUSPENDED','EXPIRED','CANCELLED','TERMINATED_EARLY')),
    start_date                  date NOT NULL,
    end_date                    date,
    auto_renew                  boolean NOT NULL DEFAULT false,
    renewal_period_months       int,
    recurrence                  text NOT NULL CHECK (recurrence IN
        ('MONTHLY','BIWEEKLY','QUARTERLY','SEMIANNUAL','ANNUAL')),
    billing_day                 int NOT NULL CHECK (billing_day BETWEEN 1 AND 31),
    payment_condition_id        uuid REFERENCES catalog_payment_condition(id),
    base_value                  numeric(15, 2) NOT NULL CHECK (base_value >= 0),
    last_adjustment_date        date,
    next_adjustment_date        date,
    cost_center_id              uuid,                              -- soft FK p/ cost_center; sem REFERENCES (cross-module table financeiro)
    cancellation_clause         text,
    cancellation_fee_value      numeric(15, 2),
    cancellation_notice_days    int,
    notes                       text,
    created_by                  uuid NOT NULL,
    activated_at                timestamptz,
    suspended_at                timestamptz,
    terminated_at               timestamptz,
    termination_reason          text,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_branch_number UNIQUE (branch_id, number),
    CONSTRAINT ck_contract_number_positive CHECK (number > 0),
    CONSTRAINT ck_contract_end_after_start CHECK (end_date IS NULL OR end_date >= start_date),
    CONSTRAINT ck_contract_renewal_period CHECK
        (auto_renew = false OR renewal_period_months IS NOT NULL)
);
CREATE INDEX idx_contract_status ON contract(status) WHERE status NOT IN ('CANCELLED', 'EXPIRED', 'TERMINATED_EARLY');
CREATE INDEX idx_contract_branch ON contract(branch_id);
CREATE INDEX idx_contract_customer ON contract(customer_id);

CREATE TABLE contract_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id     uuid NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    item_type       text NOT NULL CHECK (item_type IN ('PRODUCT', 'SERVICE')),
    packaging_id    uuid REFERENCES packaging(id),
    service_code    text,
    description     text NOT NULL,
    quantity        numeric(15, 4) NOT NULL CHECK (quantity > 0),
    unit_price      numeric(15, 4) NOT NULL CHECK (unit_price >= 0),
    discount_value  numeric(15, 2) NOT NULL DEFAULT 0,
    net_value       numeric(15, 2) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_item_line UNIQUE (contract_id, line_number),
    CONSTRAINT ck_contract_item_type_payload CHECK (
        (item_type = 'PRODUCT' AND packaging_id IS NOT NULL AND service_code IS NULL)
        OR (item_type = 'SERVICE' AND service_code IS NOT NULL AND packaging_id IS NULL)
    )
);
CREATE INDEX idx_contract_item_contract ON contract_item(contract_id);

CREATE TABLE contract_amendment (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id     uuid NOT NULL REFERENCES contract(id),
    type            text NOT NULL CHECK (type IN
        ('EXTENSION', 'VALUE', 'ITEM_ADD', 'ITEM_REMOVE', 'OTHER')),
    effective_from  date NOT NULL,
    description     text NOT NULL,
    new_end_date    date,
    new_base_value  numeric(15, 2),
    payload         jsonb,
    status          text NOT NULL CHECK (status IN ('DRAFT', 'EFFECTIVE', 'CANCELLED')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    effective_at    timestamptz,
    cancelled_at    timestamptz,
    created_by      uuid NOT NULL,
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_contract_amendment_contract ON contract_amendment(contract_id);

CREATE TABLE contract_adjustment (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    contract_id     uuid NOT NULL REFERENCES contract(id),
    applied_on      date NOT NULL,
    index_kind      text NOT NULL CHECK (index_kind IN ('IGPM', 'IPCA', 'INPC', 'MANUAL')),
    percentage      numeric(7, 4) NOT NULL,
    old_base_value  numeric(15, 2) NOT NULL,
    new_base_value  numeric(15, 2) NOT NULL,
    notes           text,
    applied_by      uuid NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_contract_adjustment_date UNIQUE (contract_id, applied_on)
);
CREATE INDEX idx_contract_adjustment_contract ON contract_adjustment(contract_id);
