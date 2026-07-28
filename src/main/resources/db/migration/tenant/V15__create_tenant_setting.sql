-- Migração aplicada POR TENANT.
-- Slice 7.3 — tenant_setting: configurações por tenant que financeiro consome
-- para preencher defaults em listeners cross-modulo (rateio default em MVF
-- gerada por SaleClosed / PurchaseReceived).
--
-- Decisão: 1 row por schema (tenant) — UNIQUE constraint força singleton.
-- Operações são "upsert" pela chave fixa (singleton_key=true).
--
-- Doc: docs/domain/17-financeiro.md.

CREATE TABLE tenant_setting (
    id                                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    singleton_key                       boolean NOT NULL DEFAULT true,
    default_revenue_chart_account_id    uuid REFERENCES chart_account(id),
    default_expense_chart_account_id    uuid REFERENCES chart_account(id),
    default_cost_center_id              uuid REFERENCES cost_center(id),
    updated_by                          uuid,
    created_at                          timestamptz NOT NULL DEFAULT now(),
    updated_at                          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (singleton_key),
    CONSTRAINT ck_tenant_setting_singleton CHECK (singleton_key = true)
);
