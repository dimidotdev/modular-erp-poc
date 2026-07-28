-- Migração aplicada POR TENANT.
-- Slice 9.2a — Itens da OS: produto consumido, serviço prestado, apontamento de mão-de-obra.
-- Integração estoque OUTBOUND (via StockInventoryPort do módulo estoque) é manual via endpoint
-- POST /products/{lineNumber}/consume — auto-consume on transition IN_PROGRESS fica para v1.1+.
--
-- O QUE NÃO ESTÁ AQUI (deferido para slice 9.2b):
-- - service_order_billing (Saga NFe + NFSe + financeiro)
-- - service_order_payment (parcelas/condição de pagamento)
-- - saga cancel pós-faturamento (estorno NFe + MVF + estoque)
--
-- Doc: docs/domain/16-ordem-servico.md.

CREATE TABLE service_order_product (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    os_id           uuid NOT NULL REFERENCES service_order(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15, 4) NOT NULL,
    unit_price      numeric(15, 4) NOT NULL,
    discount_value  numeric(15, 2) NOT NULL DEFAULT 0,
    net_value       numeric(15, 2) NOT NULL,
    consumed_at     timestamptz,
    stock_movement_id uuid,                          -- preenchido após StockInventoryPort.ship
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_os_product_line UNIQUE (os_id, line_number),
    CONSTRAINT ck_os_product_qty_positive CHECK (quantity > 0)
);
CREATE INDEX idx_os_product_os ON service_order_product(os_id);
CREATE INDEX idx_os_product_packaging ON service_order_product(packaging_id);

CREATE TABLE service_order_service (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    os_id           uuid NOT NULL REFERENCES service_order(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    service_code    text NOT NULL,                   -- código LC 116 do município
    description     text NOT NULL,
    quantity        numeric(15, 4) NOT NULL DEFAULT 1,
    unit_price      numeric(15, 4) NOT NULL,
    discount_value  numeric(15, 2) NOT NULL DEFAULT 0,
    net_value       numeric(15, 2) NOT NULL,
    issqn_rate      numeric(7, 4) NOT NULL,
    issqn_value     numeric(15, 2) NOT NULL DEFAULT 0,
    pis_cofins_calc jsonb,                            -- v1: null; 9.2b vai popular
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_os_service_line UNIQUE (os_id, line_number),
    CONSTRAINT ck_os_service_qty_positive CHECK (quantity > 0),
    CONSTRAINT ck_os_service_issqn_rate CHECK (issqn_rate >= 0 AND issqn_rate <= 100)
);
CREATE INDEX idx_os_service_os ON service_order_service(os_id);

CREATE TABLE service_order_labor (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    os_id           uuid NOT NULL REFERENCES service_order(id) ON DELETE CASCADE,
    employee_id     uuid NOT NULL REFERENCES employee(id),
    started_at      timestamptz NOT NULL,
    finished_at     timestamptz,
    hours           numeric(7, 2),
    description     text,
    internal_cost   numeric(15, 2),
    is_billable     boolean NOT NULL DEFAULT false,
    related_service_line int,                         -- aponta para service_order_service.line_number
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_os_labor_hours_positive CHECK (hours IS NULL OR hours >= 0),
    CONSTRAINT ck_os_labor_finished_after_started
        CHECK (finished_at IS NULL OR finished_at >= started_at)
);
CREATE INDEX idx_os_labor_os ON service_order_labor(os_id);
CREATE INDEX idx_os_labor_employee ON service_order_labor(employee_id);
