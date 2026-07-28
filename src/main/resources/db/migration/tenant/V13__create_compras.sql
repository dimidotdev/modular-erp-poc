-- Migração aplicada POR TENANT.
-- Slice 6.1 — Compras foundation: SC, cotação, PC, recebimento, devolução
-- a fornecedor. 11 tabelas (5 cabeçalhos + 6 filhas/itens).
--
-- FKs sem alvo: cost_center_id (financeiro slice 7), invoice_id
-- (fiscal slice 8), dfe_document_id (fiscal slice 8) ficam como uuid
-- cru. Quando módulos existirem, adicionamos FKs em migration posterior.
--
-- Doc: docs/domain/15-compras.md.

-- =====================================================================
-- purchase_request — Solicitação de Compra (SC)
-- =====================================================================

CREATE TABLE purchase_request (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint NOT NULL DEFAULT 0,
    number              bigint NOT NULL,
    branch_id           uuid NOT NULL REFERENCES branch(id),
    requested_by        uuid NOT NULL,
    department_id       uuid REFERENCES catalog_department(id),
    cost_center_id      uuid,  -- sem FK até financeiro (slice 7)
    needed_by           date,
    priority            text NOT NULL DEFAULT 'NORMAL'
        CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')),
    status              text NOT NULL CHECK (status IN
        ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','QUOTING','ORDERED','CANCELLED')),
    justification       text,
    approved_by         uuid,
    approved_at         timestamptz,
    rejected_by         uuid,
    rejected_at         timestamptz,
    rejection_reason    text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number)
);
CREATE INDEX idx_purchase_request_branch_status ON purchase_request (branch_id, status);
CREATE INDEX idx_purchase_request_requested_by ON purchase_request (requested_by);

CREATE TABLE purchase_request_item (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    request_id              uuid NOT NULL REFERENCES purchase_request(id) ON DELETE CASCADE,
    line_number             int NOT NULL,
    packaging_id            uuid REFERENCES packaging(id),  -- nulo permitido (item livre)
    description             text NOT NULL,
    quantity                numeric(15,4) NOT NULL,
    estimated_unit_cost     numeric(15,4),
    notes                   text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (request_id, line_number),
    CONSTRAINT ck_purchase_request_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_purchase_request_item_cost_non_negative
        CHECK (estimated_unit_cost IS NULL OR estimated_unit_cost >= 0)
);
CREATE INDEX idx_purchase_request_item_packaging ON purchase_request_item (packaging_id)
    WHERE packaging_id IS NOT NULL;

-- =====================================================================
-- quotation — cotação + fornecedores convidados + respostas
-- =====================================================================

CREATE TABLE quotation (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    number      bigint NOT NULL,
    request_id  uuid REFERENCES purchase_request(id),  -- nulo = cotação ad-hoc
    branch_id   uuid NOT NULL REFERENCES branch(id),
    status      text NOT NULL CHECK (status IN ('OPEN','SENT','RECEIVING','CLOSED','CANCELLED')),
    deadline    date,
    created_at  timestamptz NOT NULL DEFAULT now(),
    closed_at   timestamptz,
    updated_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number)
);
CREATE INDEX idx_quotation_request ON quotation (request_id) WHERE request_id IS NOT NULL;
CREATE INDEX idx_quotation_branch_status ON quotation (branch_id, status);

-- quotation_supplier usa PK composta (quotation_id, supplier_id).
CREATE TABLE quotation_supplier (
    quotation_id            uuid NOT NULL REFERENCES quotation(id) ON DELETE CASCADE,
    supplier_id             uuid NOT NULL REFERENCES person(id),
    status                  text NOT NULL CHECK (status IN
        ('INVITED','RESPONDED','DECLINED','EXPIRED')),
    invited_at              timestamptz NOT NULL DEFAULT now(),
    responded_at            timestamptz,
    payment_condition_id    uuid REFERENCES catalog_payment_condition(id),
    delivery_days           int,
    freight_value           numeric(15,2) NOT NULL DEFAULT 0,
    notes                   text,
    PRIMARY KEY (quotation_id, supplier_id),
    CONSTRAINT ck_quotation_supplier_freight_non_negative CHECK (freight_value >= 0)
);
CREATE INDEX idx_quotation_supplier_supplier ON quotation_supplier (supplier_id);

CREATE TABLE quotation_response (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    quotation_id            uuid NOT NULL REFERENCES quotation(id) ON DELETE CASCADE,
    supplier_id             uuid NOT NULL REFERENCES person(id),
    request_item_line       int NOT NULL,
    packaging_id            uuid REFERENCES packaging(id),
    description             text NOT NULL,
    quantity                numeric(15,4) NOT NULL,
    unit_price              numeric(15,4) NOT NULL,
    notes                   text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (quotation_id, supplier_id, request_item_line),
    CONSTRAINT ck_quotation_response_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_quotation_response_unit_price_non_negative CHECK (unit_price >= 0)
);
CREATE INDEX idx_quotation_response_packaging ON quotation_response (packaging_id)
    WHERE packaging_id IS NOT NULL;

-- =====================================================================
-- purchase_order — Pedido de Compra (PC)
-- =====================================================================

CREATE TABLE purchase_order (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version                 bigint NOT NULL DEFAULT 0,
    number                  bigint NOT NULL,
    branch_id               uuid NOT NULL REFERENCES branch(id),
    supplier_id             uuid NOT NULL REFERENCES person(id),
    quotation_id            uuid REFERENCES quotation(id),
    request_id              uuid REFERENCES purchase_request(id),
    payment_condition_id    uuid REFERENCES catalog_payment_condition(id),
    status                  text NOT NULL CHECK (status IN
        ('OPEN','SENT','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    delivery_date_expected  date,
    total_products          numeric(15,2) NOT NULL DEFAULT 0,
    total_freight           numeric(15,2) NOT NULL DEFAULT 0,
    total                   numeric(15,2) NOT NULL DEFAULT 0,
    notes                   text,
    created_at              timestamptz NOT NULL DEFAULT now(),
    updated_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number),
    CONSTRAINT ck_purchase_order_totals_non_negative
        CHECK (total_products >= 0 AND total_freight >= 0 AND total >= 0)
);
CREATE INDEX idx_purchase_order_supplier ON purchase_order (supplier_id);
CREATE INDEX idx_purchase_order_branch_status ON purchase_order (branch_id, status);
CREATE INDEX idx_purchase_order_quotation ON purchase_order (quotation_id)
    WHERE quotation_id IS NOT NULL;

CREATE TABLE purchase_order_item (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id            uuid NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
    line_number         int NOT NULL,
    packaging_id        uuid NOT NULL REFERENCES packaging(id),
    quantity_ordered    numeric(15,4) NOT NULL,
    quantity_received   numeric(15,4) NOT NULL DEFAULT 0,
    unit_price          numeric(15,4) NOT NULL,
    discount_value      numeric(15,2) NOT NULL DEFAULT 0,
    net_value           numeric(15,2) NOT NULL,
    expected_delivery   date,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (order_id, line_number),
    CONSTRAINT ck_purchase_order_item_quantity_positive CHECK (quantity_ordered > 0),
    CONSTRAINT ck_purchase_order_item_received_non_negative CHECK (quantity_received >= 0),
    CONSTRAINT ck_purchase_order_item_unit_price_non_negative CHECK (unit_price >= 0)
);
CREATE INDEX idx_purchase_order_item_packaging ON purchase_order_item (packaging_id);

-- =====================================================================
-- purchase_receipt — recebimento físico (gera INBOUND no estoque)
-- =====================================================================
-- supplier_invoice_key opcional vincula com a NFe do fornecedor (chave de
-- acesso). dfe_document_id liga ao XML sincronizado quando existir o
-- módulo fiscal (slice 8); por ora é uuid cru.

CREATE TABLE purchase_receipt (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id                    uuid NOT NULL REFERENCES purchase_order(id),
    number                      bigint NOT NULL,
    received_at                 timestamptz NOT NULL DEFAULT now(),
    supplier_invoice_key        text,
    supplier_invoice_number     text,
    dfe_document_id             uuid,  -- sem FK até fiscal (slice 8)
    freight_value               numeric(15,2) NOT NULL DEFAULT 0,
    total                       numeric(15,2) NOT NULL DEFAULT 0,
    notes                       text,
    user_id                     uuid NOT NULL,
    idempotency_key             text,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (order_id, number),
    CONSTRAINT ck_purchase_receipt_freight_non_negative CHECK (freight_value >= 0),
    CONSTRAINT ck_purchase_receipt_total_non_negative CHECK (total >= 0)
);
CREATE UNIQUE INDEX uq_purchase_receipt_idempotency
    ON purchase_receipt (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_purchase_receipt_order ON purchase_receipt (order_id);
CREATE INDEX idx_purchase_receipt_dfe ON purchase_receipt (dfe_document_id)
    WHERE dfe_document_id IS NOT NULL;

CREATE TABLE purchase_receipt_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id      uuid NOT NULL REFERENCES purchase_receipt(id) ON DELETE CASCADE,
    order_item_id   uuid NOT NULL REFERENCES purchase_order_item(id),
    quantity        numeric(15,4) NOT NULL,
    unit_cost       numeric(15,4) NOT NULL,
    batch_id        uuid REFERENCES stock_batch(id),
    location_id     uuid REFERENCES stock_location(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_purchase_receipt_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_purchase_receipt_item_unit_cost_non_negative CHECK (unit_cost >= 0)
);
CREATE INDEX idx_purchase_receipt_item_order_item ON purchase_receipt_item (order_item_id);

-- =====================================================================
-- supplier_return — devolução a fornecedor
-- =====================================================================
-- invoice_id liga à NFe de devolução de saída (módulo fiscal — slice 8).

CREATE TABLE supplier_return (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id  uuid NOT NULL REFERENCES purchase_receipt(id),
    number      bigint NOT NULL,
    issued_at   timestamptz NOT NULL DEFAULT now(),
    reason      text NOT NULL,
    total       numeric(15,2) NOT NULL DEFAULT 0,
    invoice_id  uuid,  -- sem FK até fiscal (slice 8)
    notes       text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_supplier_return_total_non_negative CHECK (total >= 0)
);
CREATE INDEX idx_supplier_return_receipt ON supplier_return (receipt_id);

CREATE TABLE supplier_return_item (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    supplier_return_id  uuid NOT NULL REFERENCES supplier_return(id) ON DELETE CASCADE,
    receipt_item_id     uuid NOT NULL REFERENCES purchase_receipt_item(id),
    quantity            numeric(15,4) NOT NULL,
    unit_cost           numeric(15,4) NOT NULL,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_supplier_return_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_supplier_return_item_unit_cost_non_negative CHECK (unit_cost >= 0)
);
CREATE INDEX idx_supplier_return_item_receipt_item ON supplier_return_item (receipt_item_id);
