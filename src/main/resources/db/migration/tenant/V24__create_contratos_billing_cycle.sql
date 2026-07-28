-- Migração aplicada POR TENANT.
-- Slice 10.2 — Contratos billing cycles: registros de "rodada" de faturamento por competência
-- (Janeiro, Fevereiro, etc). Gerados pelo GenerateBillingCyclesJob (cron diário com ShedLock).
-- Listener no financeiro consome ContractInvoiceGeneratedEvent → cria MVF a receber.
--
-- Doc: docs/domain/18-contratos.md.

CREATE TABLE contract_billing_cycle (
    id                   uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version              bigint NOT NULL DEFAULT 0,
    contract_id          uuid NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    competence_month     date NOT NULL,        -- '2026-05-01' (sempre dia 1)
    due_date             date NOT NULL,
    value                numeric(15, 2) NOT NULL CHECK (value >= 0),
    financial_record_id  uuid,                  -- soft FK p/ financeiro.financial_record
    invoice_id           uuid,                  -- soft FK p/ fiscal.invoice (NFSe quando serviço)
    status               text NOT NULL CHECK (status IN ('PENDING', 'GENERATED', 'FAILED', 'SKIPPED')),
    generated_at         timestamptz,
    failure_reason       text,
    notes                text,
    created_at           timestamptz NOT NULL DEFAULT now(),
    updated_at           timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT uq_billing_cycle_unique UNIQUE (contract_id, competence_month)
);

CREATE INDEX idx_billing_cycle_pending
    ON contract_billing_cycle(competence_month)
    WHERE status = 'PENDING';
CREATE INDEX idx_billing_cycle_failed
    ON contract_billing_cycle(competence_month)
    WHERE status = 'FAILED';
CREATE INDEX idx_billing_cycle_contract
    ON contract_billing_cycle(contract_id, competence_month DESC);

-- ShedLock platform table (cross-tenant infra; vive no schema 'platform').
-- Não precisamos criar aqui se já existe schema platform com a tabela; conferir antes.
-- Se não houver, será criado por migration GLOBAL futura (não aqui).
