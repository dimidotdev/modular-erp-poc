-- Migração aplicada POR TENANT.
-- Slice 5.1 — Movimentação foundation: pedido, venda, devolução, transferência,
-- saída avulsa, ajuste de inventário. 12 tabelas (6 cabeçalhos + 6 itens).
--
-- IMPORTANTE: `invoice_id` em sale/sale_return/stock_transfer fica como uuid
-- SEM foreign key — o módulo fiscal ainda não foi criado (slice 8). Quando
-- existir, adicionamos o FK em V_n+1 sem afetar dados existentes.
--
-- Restrições entre linhas (ex.: soma devolvida ≤ quantidade vendida) ficam na
-- camada de aplicação porque PostgreSQL não permite subqueries em CHECK.
-- Uma trigger pode fornecer proteção adicional quando necessário.
--
-- Doc: docs/domain/14-movimentacao.md.

-- =====================================================================
-- sales_order — pedido (intenção, sem efeito em estoque/financeiro)
-- =====================================================================

CREATE TABLE sales_order (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version               bigint NOT NULL DEFAULT 0,
    branch_id             uuid NOT NULL REFERENCES branch(id),
    customer_id           uuid NOT NULL REFERENCES person(id),
    salesperson_id        uuid REFERENCES person(id),
    payment_condition_id  uuid REFERENCES catalog_payment_condition(id),
    price_table_id        uuid REFERENCES price_table(id),
    number                bigint NOT NULL,
    status                text NOT NULL CHECK (status IN
        ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','CONVERTED','CANCELLED','EXPIRED')),
    valid_until           date,
    total_gross           numeric(15,2) NOT NULL DEFAULT 0,
    total_discount        numeric(15,2) NOT NULL DEFAULT 0,
    total                 numeric(15,2) NOT NULL DEFAULT 0,
    notes                 text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    converted_at          timestamptz,
    converted_sale_id     uuid,
    UNIQUE (branch_id, number),
    CONSTRAINT ck_sales_order_totals_non_negative
        CHECK (total_gross >= 0 AND total_discount >= 0 AND total >= 0)
);
CREATE INDEX idx_sales_order_customer ON sales_order (customer_id);
CREATE INDEX idx_sales_order_branch_status ON sales_order (branch_id, status);

CREATE TABLE sales_order_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    order_id        uuid NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15,4) NOT NULL,
    unit_price      numeric(15,4) NOT NULL,
    discount_pct    numeric(7,4) NOT NULL DEFAULT 0,
    discount_value  numeric(15,2) NOT NULL DEFAULT 0,
    net_value       numeric(15,2) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (order_id, line_number),
    CONSTRAINT ck_sales_order_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_sales_order_item_unit_price_non_negative CHECK (unit_price >= 0),
    CONSTRAINT ck_sales_order_item_discount_range CHECK (discount_pct >= 0 AND discount_pct <= 1)
);
CREATE INDEX idx_sales_order_item_packaging ON sales_order_item (packaging_id);

-- =====================================================================
-- sale — venda (saída efetiva, baixa estoque, gera MVF)
-- =====================================================================
-- idempotency_key UNIQUE quando informado: RN-MOV-004 garante que close
-- idempotente não cria duas vendas para o mesmo POST.

CREATE TABLE sale (
    id                    uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version               bigint NOT NULL DEFAULT 0,
    branch_id             uuid NOT NULL REFERENCES branch(id),
    customer_id           uuid NOT NULL REFERENCES person(id),
    salesperson_id        uuid REFERENCES person(id),
    sales_order_id        uuid REFERENCES sales_order(id),
    payment_condition_id  uuid REFERENCES catalog_payment_condition(id),
    number                bigint NOT NULL,
    status                text NOT NULL CHECK (status IN
        ('OPEN','CLOSED','CANCELLATION_IN_PROGRESS','CANCELLED','PARTIAL_CANCELLATION_FAILED','LOCKED_FOR_CANCELLATION')),
    issued_at             timestamptz NOT NULL DEFAULT now(),
    closed_at             timestamptz,
    cancelled_at          timestamptz,
    cancellation_reason   text,
    total_gross           numeric(15,2) NOT NULL DEFAULT 0,
    total_discount        numeric(15,2) NOT NULL DEFAULT 0,
    total_freight         numeric(15,2) NOT NULL DEFAULT 0,
    total                 numeric(15,2) NOT NULL DEFAULT 0,
    invoice_id            uuid,  -- sem FK: fiscal vem no slice 8
    notes                 text,
    idempotency_key       text,
    created_at            timestamptz NOT NULL DEFAULT now(),
    updated_at            timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number),
    CONSTRAINT ck_sale_totals_non_negative
        CHECK (total_gross >= 0 AND total_discount >= 0 AND total_freight >= 0 AND total >= 0)
);
CREATE UNIQUE INDEX uq_sale_idempotency
    ON sale (idempotency_key) WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_sale_status_date ON sale (status, issued_at DESC);
CREATE INDEX idx_sale_customer ON sale (customer_id);
CREATE INDEX idx_sale_order ON sale (sales_order_id) WHERE sales_order_id IS NOT NULL;

CREATE TABLE sale_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id         uuid NOT NULL REFERENCES sale(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15,4) NOT NULL,
    unit_price      numeric(15,4) NOT NULL,
    discount_value  numeric(15,2) NOT NULL DEFAULT 0,
    net_value       numeric(15,2) NOT NULL,
    cfop            char(4),
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (sale_id, line_number),
    CONSTRAINT ck_sale_item_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_sale_item_unit_price_non_negative CHECK (unit_price >= 0)
);
CREATE INDEX idx_sale_item_packaging ON sale_item (packaging_id);

-- =====================================================================
-- sale_return — devolução (parcial ou total)
-- =====================================================================
-- 3 quantidades por item: returned (volta ao estoque), lost (perda total),
-- used (cliente consumiu) — RN-MOV-014/015. Soma ≤ sale_item.quantity é
-- enforced no service (CHECK com subquery não é suportado em Postgres).

CREATE TABLE sale_return (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sale_id     uuid NOT NULL REFERENCES sale(id),
    branch_id   uuid NOT NULL REFERENCES branch(id),
    status      text NOT NULL CHECK (status IN ('OPEN','CLOSED','CANCELLED')),
    issued_at   timestamptz NOT NULL DEFAULT now(),
    closed_at   timestamptz,
    reason      text NOT NULL,
    total       numeric(15,2) NOT NULL DEFAULT 0,
    invoice_id  uuid,  -- sem FK até slice 8
    notes       text,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_sale_return_total_non_negative CHECK (total >= 0)
);
CREATE INDEX idx_sale_return_sale ON sale_return (sale_id);
CREATE INDEX idx_sale_return_branch_status ON sale_return (branch_id, status);

CREATE TABLE sale_return_item (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    return_id           uuid NOT NULL REFERENCES sale_return(id) ON DELETE CASCADE,
    sale_item_id        uuid NOT NULL REFERENCES sale_item(id),
    quantity_returned   numeric(15,4) NOT NULL DEFAULT 0,
    quantity_lost       numeric(15,4) NOT NULL DEFAULT 0,
    quantity_used       numeric(15,4) NOT NULL DEFAULT 0,
    refund_value        numeric(15,2) NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (return_id, sale_item_id),
    CONSTRAINT ck_sale_return_item_quantities_non_negative
        CHECK (quantity_returned >= 0 AND quantity_lost >= 0 AND quantity_used >= 0),
    CONSTRAINT ck_sale_return_item_refund_non_negative
        CHECK (refund_value >= 0)
);

-- =====================================================================
-- stock_transfer — transferência entre filiais/locais
-- =====================================================================
-- Quando origin_branch != dest_branch com CNPJs distintos: gera NFe (RN-MOV-020).
-- Mesma filial entre locais: sem NFe (RN-MOV-021). Decisão fica no service.

CREATE TABLE stock_transfer (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    number              bigint NOT NULL,
    origin_branch_id    uuid NOT NULL REFERENCES branch(id),
    dest_branch_id      uuid NOT NULL REFERENCES branch(id),
    origin_location_id  uuid REFERENCES stock_location(id),
    dest_location_id    uuid REFERENCES stock_location(id),
    status              text NOT NULL CHECK (status IN ('OPEN','CLOSED','CANCELLED')),
    issued_at           timestamptz NOT NULL DEFAULT now(),
    closed_at           timestamptz,
    invoice_id          uuid,  -- sem FK até slice 8
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (origin_branch_id, number)
);
CREATE INDEX idx_stock_transfer_dest ON stock_transfer (dest_branch_id);
CREATE INDEX idx_stock_transfer_status ON stock_transfer (status);

CREATE TABLE stock_transfer_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    transfer_id     uuid NOT NULL REFERENCES stock_transfer(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15,4) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (transfer_id, line_number),
    CONSTRAINT ck_stock_transfer_item_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX idx_stock_transfer_item_packaging ON stock_transfer_item (packaging_id);

-- =====================================================================
-- outbound_voucher — saída avulsa (amostra, brinde, garantia, perda)
-- =====================================================================
-- RN-MOV-022: reduz estoque sem MVF. NFe opcional configurável.

CREATE TABLE outbound_voucher (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    number               bigint NOT NULL,
    branch_id            uuid NOT NULL REFERENCES branch(id),
    reason               text NOT NULL CHECK (reason IN ('SAMPLE','GIFT','WARRANTY','LOSS','OTHER')),
    issued_at            timestamptz NOT NULL DEFAULT now(),
    recipient_person_id  uuid REFERENCES person(id),
    notes                text,
    created_at           timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number)
);
CREATE INDEX idx_outbound_voucher_branch_date ON outbound_voucher (branch_id, issued_at DESC);

CREATE TABLE outbound_voucher_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    voucher_id      uuid NOT NULL REFERENCES outbound_voucher(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15,4) NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (voucher_id, line_number),
    CONSTRAINT ck_outbound_voucher_item_quantity_positive CHECK (quantity > 0)
);
CREATE INDEX idx_outbound_voucher_item_packaging ON outbound_voucher_item (packaging_id);

-- =====================================================================
-- stock_inventory_adjustment — ajuste manual com conferência
-- =====================================================================
-- RN-MOV-024: linha-a-linha com expected_qty + counted_qty + delta GENERATED.

CREATE TABLE stock_inventory_adjustment (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    number      bigint NOT NULL,
    branch_id   uuid NOT NULL REFERENCES branch(id),
    issued_at   timestamptz NOT NULL DEFAULT now(),
    reason      text NOT NULL,
    notes       text,
    user_id     uuid NOT NULL,
    created_at  timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number)
);
CREATE INDEX idx_stock_inventory_adjustment_branch_date
    ON stock_inventory_adjustment (branch_id, issued_at DESC);

CREATE TABLE stock_inventory_adjustment_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    adjustment_id   uuid NOT NULL REFERENCES stock_inventory_adjustment(id) ON DELETE CASCADE,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    location_id     uuid NOT NULL REFERENCES stock_location(id),
    expected_qty    numeric(15,4) NOT NULL,
    counted_qty     numeric(15,4) NOT NULL,
    delta           numeric(15,4) GENERATED ALWAYS AS (counted_qty - expected_qty) STORED,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_stock_inventory_adjustment_item_qty_non_negative
        CHECK (expected_qty >= 0 AND counted_qty >= 0)
);
CREATE INDEX idx_stock_inventory_adjustment_item_packaging
    ON stock_inventory_adjustment_item (packaging_id);
