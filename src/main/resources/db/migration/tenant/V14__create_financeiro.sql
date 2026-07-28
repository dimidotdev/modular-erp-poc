-- Migração aplicada POR TENANT.
-- Slice 7.1 — Financeiro foundation: plano de contas, centro de custo,
-- conta bancária, MVF, parcelas, rateios, pagamentos, estornos,
-- congelamento de período. 10 tabelas.
--
-- Tabelas CNAB (cnab_batch/cnab_batch_record) e conciliação
-- (bank_statement_line) ficam para slices 7.4/7.5.
--
-- FKs em compras.purchase_*.cost_center_id ficam para slice de backfill
-- futuro. Por ora, cost_center_id em purchase_request/purchase_order
-- permanece como uuid cru sem REFERENCES.
--
-- Doc: docs/domain/17-financeiro.md.

-- =====================================================================
-- chart_account — Plano de Contas
-- =====================================================================
-- Estrutura hierárquica (parent_id self-FK). Códigos estruturais no
-- estilo '3.01.001'. Contas sintéticas agregam filhas; contas
-- analíticas (is_synthetic=false) recebem lançamentos.

CREATE TABLE chart_account (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    type            text NOT NULL CHECK (type IN
        ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE','RESULT')),
    parent_id       uuid REFERENCES chart_account(id),
    is_synthetic    boolean NOT NULL DEFAULT false,
    is_active       boolean NOT NULL DEFAULT true,
    sort_order      int NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_chart_account_parent ON chart_account (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_chart_account_type_active ON chart_account (type) WHERE is_active;

-- =====================================================================
-- cost_center — Centro de Custo
-- =====================================================================
-- Hierárquico via parent_id. Diferente de chart_account: não tem
-- 'type' nem 'is_synthetic' — toda CC pode receber lançamento.

CREATE TABLE cost_center (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    parent_id       uuid REFERENCES cost_center(id),
    is_active       boolean NOT NULL DEFAULT true,
    sort_order      int NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_cost_center_parent ON cost_center (parent_id) WHERE parent_id IS NOT NULL;
CREATE INDEX idx_cost_center_active ON cost_center (is_active) WHERE is_active;

-- =====================================================================
-- bank_account — Conta Bancária / Caixa
-- =====================================================================
-- Tipos: CASH (caixa físico), BANK (conta corrente), CARD (maquininha),
-- GATEWAY (PIX/Mercado Pago/etc), VOUCHER (vale-presente, crédito).
-- Campos FEBRABAN só fazem sentido para BANK; nullable nos demais.

CREATE TABLE bank_account (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                text NOT NULL UNIQUE,
    name                text NOT NULL,
    type                text NOT NULL CHECK (type IN
        ('CASH','BANK','CARD','GATEWAY','VOUCHER')),
    bank_code           text,  -- FEBRABAN; nullable para CASH/CARD/VOUCHER
    bank_agency         text,
    bank_account_no     text,
    chart_account_id    uuid NOT NULL REFERENCES chart_account(id),
    initial_balance     numeric(15,2) NOT NULL DEFAULT 0,
    is_active           boolean NOT NULL DEFAULT true,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_bank_account_type_active ON bank_account (type) WHERE is_active;

-- =====================================================================
-- financial_record — MVF (Movimentação Financeira / Título)
-- =====================================================================
-- Cabeçalho do título a receber (CREDIT) ou a pagar (DEBIT). Origem
-- amarra ao documento gerador (venda, compra, OS, contrato) ou MANUAL.
-- origin_id: SALE=sale.id, PURCHASE=purchase_receipt.id, OS=os.id,
-- CONTRACT=contract_invoice.id, MANUAL=null.

CREATE TABLE financial_record (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint NOT NULL DEFAULT 0,
    number              bigint NOT NULL,
    branch_id           uuid NOT NULL REFERENCES branch(id),
    direction           text NOT NULL CHECK (direction IN ('CREDIT','DEBIT')),
    counterparty_id     uuid NOT NULL REFERENCES person(id),
    document_number     text,
    issued_at           timestamptz NOT NULL DEFAULT now(),
    total_value         numeric(15,2) NOT NULL,
    status              text NOT NULL CHECK (status IN
        ('OPEN','PARTIALLY_PAID','PAID','CANCELLED','RENEGOTIATED')),
    origin_type         text NOT NULL CHECK (origin_type IN
        ('SALE','PURCHASE','OS','CONTRACT','MANUAL')),
    origin_id           uuid,  -- SALE=sale.id, PURCHASE=purchase_receipt.id, OS=os.id, CONTRACT=contract_invoice.id, MANUAL=null
    notes               text,
    idempotency_key     text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number),
    CONSTRAINT ck_financial_record_total_positive CHECK (total_value > 0)
);
CREATE UNIQUE INDEX uq_financial_record_origin
    ON financial_record (origin_type, origin_id)
    WHERE origin_type <> 'MANUAL' AND origin_id IS NOT NULL;
CREATE UNIQUE INDEX uq_financial_record_idempotency
    ON financial_record (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_financial_record_branch_status ON financial_record (branch_id, status);
CREATE INDEX idx_financial_record_counterparty ON financial_record (counterparty_id);

-- =====================================================================
-- financial_installment — Parcela do MVF
-- =====================================================================
-- Quebra do total_value do financial_record em N parcelas com
-- vencimentos próprios. paid_value acumula via aplicações de pagamento;
-- juros/multa/desconto são aplicados no momento do recebimento.

CREATE TABLE financial_installment (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id           uuid NOT NULL REFERENCES financial_record(id) ON DELETE CASCADE,
    sequence            int NOT NULL,
    due_date            date NOT NULL,
    original_value      numeric(15,2) NOT NULL,
    paid_value          numeric(15,2) NOT NULL DEFAULT 0,
    fine_value          numeric(15,2) NOT NULL DEFAULT 0,
    interest_value      numeric(15,2) NOT NULL DEFAULT 0,
    discount_value      numeric(15,2) NOT NULL DEFAULT 0,
    status              text NOT NULL CHECK (status IN
        ('OPEN','PARTIALLY_PAID','PAID','CANCELLED','RENEGOTIATED')),
    bank_account_id     uuid REFERENCES bank_account(id),
    barcode             text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (record_id, sequence),
    CONSTRAINT ck_installment_original_positive CHECK (original_value > 0),
    CONSTRAINT ck_installment_paid_nonneg CHECK (paid_value >= 0),
    CONSTRAINT ck_installment_fine_nonneg CHECK (fine_value >= 0),
    CONSTRAINT ck_installment_interest_nonneg CHECK (interest_value >= 0),
    CONSTRAINT ck_installment_discount_nonneg CHECK (discount_value >= 0)
);
CREATE INDEX idx_installment_due ON financial_installment (due_date)
    WHERE status IN ('OPEN','PARTIALLY_PAID');
CREATE INDEX idx_installment_record ON financial_installment (record_id);

-- =====================================================================
-- financial_apportionment — Rateio contábil/gerencial do MVF
-- =====================================================================
-- Distribui o total do financial_record entre N contas contábeis e
-- centros de custo. percentage é opcional (informativo); fonte da
-- verdade é o campo value (somatório deve bater com total_value do
-- record — validado em service).

CREATE TABLE financial_apportionment (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    record_id           uuid NOT NULL REFERENCES financial_record(id) ON DELETE CASCADE,
    chart_account_id    uuid NOT NULL REFERENCES chart_account(id),
    cost_center_id      uuid REFERENCES cost_center(id),
    value               numeric(15,2) NOT NULL,
    percentage          numeric(5,4),
    notes               text,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_apportionment_value_positive CHECK (value > 0),
    CONSTRAINT ck_apportionment_percentage_range CHECK (
        percentage IS NULL OR (percentage > 0 AND percentage <= 1)
    )
);
CREATE INDEX idx_apportionment_record ON financial_apportionment (record_id);
CREATE INDEX idx_apportionment_chart ON financial_apportionment (chart_account_id);

-- =====================================================================
-- financial_payment — Pagamento / Recebimento (linha)
-- =====================================================================
-- 1 batch pode produzir N linhas de payment quando o usuário liquida
-- múltiplos records num único ato com métodos/contas distintas (ex: R$
-- 100 em PIX + R$ 50 em dinheiro). batch_id agrupa as linhas; cada
-- linha tem 1 bank_account + 1 method.

CREATE TABLE financial_payment (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version             bigint NOT NULL DEFAULT 0,
    batch_id            uuid NOT NULL,  -- 1 batch pode produzir N payment rows (multi-método)
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    bank_account_id     uuid NOT NULL REFERENCES bank_account(id),
    method              text NOT NULL CHECK (method IN
        ('CASH','PIX','BOLETO','TRANSFER','CARD_DEBIT','CARD_CREDIT','CHECK','OTHER')),
    user_id             uuid NOT NULL,
    notes               text,
    idempotency_key     text,
    created_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_financial_payment_idempotency
    ON financial_payment (idempotency_key)
    WHERE idempotency_key IS NOT NULL;
CREATE INDEX idx_financial_payment_batch ON financial_payment (batch_id);
CREATE INDEX idx_financial_payment_bank ON financial_payment (bank_account_id);

-- =====================================================================
-- financial_payment_application — Aplicação de Pagamento em Parcela
-- =====================================================================
-- Liga uma linha de payment a uma parcela específica, com valores
-- decompostos (principal, juros aplicados, multa aplicada, desconto
-- concedido). N:M entre payment e installment.

CREATE TABLE financial_payment_application (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    payment_id          uuid NOT NULL REFERENCES financial_payment(id) ON DELETE CASCADE,
    installment_id      uuid NOT NULL REFERENCES financial_installment(id),
    value               numeric(15,2) NOT NULL,
    fine_applied        numeric(15,2) NOT NULL DEFAULT 0,
    interest_applied    numeric(15,2) NOT NULL DEFAULT 0,
    discount_applied    numeric(15,2) NOT NULL DEFAULT 0,
    created_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_payment_application_value_positive CHECK (value > 0),
    CONSTRAINT ck_payment_application_fine_nonneg CHECK (fine_applied >= 0),
    CONSTRAINT ck_payment_application_interest_nonneg CHECK (interest_applied >= 0),
    CONSTRAINT ck_payment_application_discount_nonneg CHECK (discount_applied >= 0)
);
CREATE INDEX idx_payment_application_installment ON financial_payment_application (installment_id);
CREATE INDEX idx_payment_application_payment ON financial_payment_application (payment_id);

-- =====================================================================
-- financial_reversal — Estorno de Pagamento
-- =====================================================================
-- Materializa o estorno como par (original_payment, reversal_payment) —
-- o reversal_payment é uma linha em financial_payment com valores
-- negativos/compensatórios. UNIQUE (original_payment_id) garante 1
-- pagamento ↔ no máximo 1 estorno.

CREATE TABLE financial_reversal (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    original_payment_id     uuid NOT NULL REFERENCES financial_payment(id),
    reversal_payment_id     uuid NOT NULL REFERENCES financial_payment(id),
    reason                  text NOT NULL,
    occurred_at             timestamptz NOT NULL DEFAULT now(),
    user_id                 uuid NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (original_payment_id),
    CONSTRAINT ck_reversal_distinct_payments CHECK (original_payment_id <> reversal_payment_id)
);
CREATE INDEX idx_reversal_reversal_payment ON financial_reversal (reversal_payment_id);

-- =====================================================================
-- frozen_period — Congelamento de Período
-- =====================================================================
-- Bloqueia alterações financeiras dentro do intervalo [period_start,
-- period_end]. branch_id NULL = congela todas as filiais (global).
-- Validação de sobreposição fica no service.

CREATE TABLE frozen_period (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id       uuid REFERENCES branch(id),  -- nullable: NULL = todas as filiais
    period_start    date NOT NULL,
    period_end      date NOT NULL,
    frozen_at       timestamptz NOT NULL DEFAULT now(),
    frozen_by       uuid NOT NULL,
    reason          text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_frozen_period_range CHECK (period_start <= period_end)
);
CREATE UNIQUE INDEX uq_frozen_period_branch_global
    ON frozen_period (period_start, period_end)
    WHERE branch_id IS NULL;
CREATE UNIQUE INDEX uq_frozen_period_branch_scoped
    ON frozen_period (branch_id, period_start, period_end)
    WHERE branch_id IS NOT NULL;
CREATE INDEX idx_frozen_period_branch ON frozen_period (branch_id) WHERE branch_id IS NOT NULL;
