-- Migração aplicada POR TENANT.
-- Slice 9.2b — Billing da OS: faturamento simultâneo NFe (produtos) + NFSe (serviços) + listener
-- no financeiro pra gerar MVF a receber. Saga inversa cobre cancelamento (estorno NFe + MVF +
-- estoque consumido).
--
-- Doc: docs/domain/16-ordem-servico.md.

CREATE TABLE service_order_billing (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version           bigint NOT NULL DEFAULT 0,
    os_id             uuid NOT NULL REFERENCES service_order(id),
    billing_type      text NOT NULL CHECK (billing_type IN ('PARTIAL', 'FINAL')),
    issued_at         timestamptz NOT NULL DEFAULT now(),
    nfe_invoice_id    uuid,                              -- soft FK p/ invoice; sem REFERENCES
                                                         -- porque schema cruza módulos
    nfse_invoice_id   uuid,                              -- soft FK p/ invoice (NFSe modelo)
    products_amount   numeric(15, 2) NOT NULL DEFAULT 0,
    services_amount   numeric(15, 2) NOT NULL DEFAULT 0,
    total             numeric(15, 2) NOT NULL,
    notes             text,
    issued_by         uuid NOT NULL,
    idempotency_key   text,                              -- header da requisição POST /bill
    cancelled_at      timestamptz,
    cancellation_reason text,
    cancelled_by      uuid,
    created_at        timestamptz NOT NULL DEFAULT now(),
    updated_at        timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_os_billing_total_nonneg CHECK (total >= 0),
    CONSTRAINT ck_os_billing_has_invoice CHECK (
        nfe_invoice_id IS NOT NULL OR nfse_invoice_id IS NOT NULL
    )
);
CREATE INDEX idx_os_billing_os ON service_order_billing(os_id);
CREATE INDEX idx_os_billing_idem
    ON service_order_billing(idempotency_key) WHERE idempotency_key IS NOT NULL;
