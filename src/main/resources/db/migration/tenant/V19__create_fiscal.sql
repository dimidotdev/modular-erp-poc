-- Migração aplicada POR TENANT.
-- Slice 8.1 — Fiscal foundation: invoice_series, invoice, invoice_item, tax_calculation, fiscal_event.
-- Schema agnóstico (NFE/NFCE/NFSE); ACBr adapter + FiscalProvider port + services entram em 8.2+.
--
-- O QUE NÃO ESTÁ AQUI (deferido por design):
-- - dfe_document / dfe_sync_state (slice 8.5)
-- - invoice.version (defer até race comprovada; V18 mostra ALTER trivial)
-- - invoice.status CONTINGENCY (slice 8.6)
-- - fiscal_event.event_type CANCELLATION/CCE/INUTILIZATION/MANIFESTATION (slice 8.6)
-- - FKs reversas em sale/purchase_receipt/etc → invoice (slice 8.x backfill)
--
-- ATENÇÃO: fiscal_event É APPEND-ONLY. Sem updated_at, sem @PreUpdate em Java.
--
-- Doc: docs/domain/13-fiscal.md.

-- =============================================================================
-- 1) invoice_series — controle de numeração por filial/modelo/série
-- =============================================================================
CREATE TABLE invoice_series (
    id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id    uuid NOT NULL REFERENCES branch(id),
    model        text NOT NULL CHECK (model IN ('NFE', 'NFCE', 'NFSE')),
    series       text NOT NULL,
    next_number  bigint NOT NULL DEFAULT 1,
    is_active    boolean NOT NULL DEFAULT true,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_series_branch_model_series UNIQUE (branch_id, model, series),
    CONSTRAINT ck_invoice_series_next_number_positive CHECK (next_number > 0)
);

-- =============================================================================
-- 2) invoice — root agregado (SEM version no 8.1; ver banner)
-- =============================================================================
CREATE TABLE invoice (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id               uuid NOT NULL REFERENCES branch(id),
    model                   text NOT NULL CHECK (model IN ('NFE', 'NFCE', 'NFSE')),
    series                  text NOT NULL,
    number                  bigint NOT NULL,
    issued_at               timestamptz NOT NULL DEFAULT now(),
    status                  text NOT NULL CHECK (status IN ('DRAFT', 'PENDING', 'AUTHORIZED', 'REJECTED', 'CANCELLED', 'INUTILIZED')),
    operation               text NOT NULL CHECK (operation IN ('SAIDA', 'ENTRADA', 'TRANSFERENCIA', 'AJUSTE', 'DEVOLUCAO_CLIENTE', 'DEVOLUCAO_FORNECEDOR')),
    purpose                 text NOT NULL CHECK (purpose IN ('NORMAL', 'COMPLEMENTAR', 'AJUSTE', 'DEVOLUCAO')),
    consumer_type           text NOT NULL CHECK (consumer_type IN ('NORMAL', 'FINAL')),
    payment_method          text CHECK (payment_method IS NULL OR payment_method IN ('AVISTA', 'APRAZO', 'OUTRO')),
    customer_person_id      uuid REFERENCES person(id),
    customer_cpf_cnpj       text,
    origin_type             text NOT NULL CHECK (origin_type IN ('SALE', 'PURCHASE', 'OS', 'MANUAL', 'CONTRACT')),
    origin_id               uuid,
    fiscal_key              char(44),
    nfe_environment         text NOT NULL CHECK (nfe_environment IN ('PRODUCAO', 'HOMOLOGACAO')),
    sefaz_protocol          text,
    sefaz_authorized_at     timestamptz,
    sefaz_rejection_code    text,
    sefaz_rejection_reason  text,
    cancellation_protocol   text,
    cancelled_at            timestamptz,
    cancellation_reason     text,
    total_products          numeric(15, 2) NOT NULL DEFAULT 0,
    total_discount          numeric(15, 2) NOT NULL DEFAULT 0,
    total_freight           numeric(15, 2) NOT NULL DEFAULT 0,
    total_taxes             numeric(15, 2) NOT NULL DEFAULT 0,
    total                   numeric(15, 2) NOT NULL DEFAULT 0,
    notes                   text,
    xml_storage_path        text,
    danfe_storage_path      text,
    idempotency_key         text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_branch_model_series_number UNIQUE (branch_id, model, series, number),
    CONSTRAINT ck_invoice_totals_nonneg CHECK (
        total_products >= 0
        AND total_discount >= 0
        AND total_freight >= 0
        AND total_taxes >= 0
        AND total >= 0
    )
);

CREATE UNIQUE INDEX uq_invoice_idempotency_key
    ON invoice (idempotency_key)
    WHERE idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX uq_invoice_fiscal_key
    ON invoice (fiscal_key)
    WHERE fiscal_key IS NOT NULL;

CREATE UNIQUE INDEX uq_invoice_origin
    ON invoice (origin_type, origin_id, model)
    WHERE origin_id IS NOT NULL;

CREATE INDEX idx_invoice_branch_status
    ON invoice (branch_id, status);

CREATE INDEX idx_invoice_customer
    ON invoice (customer_person_id)
    WHERE customer_person_id IS NOT NULL;

CREATE INDEX idx_invoice_origin
    ON invoice (origin_type, origin_id)
    WHERE origin_id IS NOT NULL;

-- =============================================================================
-- 3) invoice_item — linhas do documento fiscal
-- =============================================================================
CREATE TABLE invoice_item (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id        uuid NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    line_number       int NOT NULL,
    packaging_id      uuid REFERENCES packaging(id),
    item_code         text,
    item_description  text NOT NULL,
    ncm               char(8),
    cest              char(7),
    cfop              char(4) NOT NULL,
    origin            text NOT NULL CHECK (origin IN ('0', '1', '2', '3', '4', '5', '6', '7', '8')),
    quantity          numeric(15, 4) NOT NULL,
    unit_commercial   text NOT NULL,
    unit_fiscal       text NOT NULL,
    unit_value        numeric(15, 4) NOT NULL,
    gross_value       numeric(15, 2) NOT NULL,
    discount          numeric(15, 2) NOT NULL DEFAULT 0,
    net_value         numeric(15, 2) NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_invoice_item_invoice_line UNIQUE (invoice_id, line_number),
    CONSTRAINT ck_invoice_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_invoice_item_unit_value_nonneg CHECK (unit_value >= 0),
    CONSTRAINT ck_invoice_item_values_nonneg CHECK (
        gross_value >= 0
        AND discount >= 0
        AND net_value >= 0
    )
);

-- =============================================================================
-- 4) tax_calculation — cálculo tributário por item × imposto
-- =============================================================================
CREATE TABLE tax_calculation (
    id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_item_id  uuid NOT NULL REFERENCES invoice_item(id) ON DELETE CASCADE,
    tax              text NOT NULL CHECK (tax IN ('ICMS', 'ICMS_ST', 'IPI', 'PIS', 'COFINS', 'ISSQN', 'FCP', 'IBPT')),
    cst_csosn        text,
    base             numeric(15, 2) NOT NULL DEFAULT 0,
    rate             numeric(7, 4) NOT NULL DEFAULT 0,
    value            numeric(15, 2) NOT NULL DEFAULT 0,
    base_st          numeric(15, 2) DEFAULT 0,
    rate_st          numeric(7, 4) DEFAULT 0,
    value_st         numeric(15, 2) DEFAULT 0,
    mva              numeric(7, 4),
    benefit_code     text,
    additional       jsonb,
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_tax_calculation_item_tax UNIQUE (invoice_item_id, tax),
    CONSTRAINT ck_tax_calculation_base_nonneg CHECK (base >= 0),
    CONSTRAINT ck_tax_calculation_value_nonneg CHECK (value >= 0)
);

-- =============================================================================
-- 5) fiscal_event — APPEND-ONLY (sem updated_at; ver banner)
-- =============================================================================
CREATE TABLE fiscal_event (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    invoice_id        uuid NOT NULL REFERENCES invoice(id),
    event_type        text NOT NULL CHECK (event_type IN ('AUTHORIZATION', 'REJECTION')),
    occurred_at       timestamptz NOT NULL DEFAULT now(),
    sefaz_protocol    text,
    sefaz_status      text,
    payload_xml_path  text,
    notes             text,
    user_id           uuid NOT NULL,
    created_at        timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_fiscal_event_invoice
    ON fiscal_event (invoice_id);
