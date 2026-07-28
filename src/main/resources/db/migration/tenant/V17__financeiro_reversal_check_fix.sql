-- Migração aplicada POR TENANT. Audit-2: estorno (POST /payments/{id}/reverse)
-- precisa persistir aplicações com valores negativos espelhando as originais.
-- Adiciona is_reversal flag em financial_payment e financial_payment_application
-- + troca CHECKs por versões condicionais por flag. RN-FIN-013.

-- 1) flag is_reversal nas duas tabelas
ALTER TABLE financial_payment
    ADD COLUMN is_reversal boolean NOT NULL DEFAULT false;

ALTER TABLE financial_payment_application
    ADD COLUMN is_reversal boolean NOT NULL DEFAULT false;

-- 2) drop dos CHECKs antigos (incondicionais) em financial_payment_application
ALTER TABLE financial_payment_application
    DROP CONSTRAINT IF EXISTS ck_payment_application_value_positive;
ALTER TABLE financial_payment_application
    DROP CONSTRAINT IF EXISTS ck_payment_application_fine_nonneg;
ALTER TABLE financial_payment_application
    DROP CONSTRAINT IF EXISTS ck_payment_application_interest_nonneg;
ALTER TABLE financial_payment_application
    DROP CONSTRAINT IF EXISTS ck_payment_application_discount_nonneg;

-- 3) re-adicionar CHECKs condicionais por flag is_reversal
--    fluxo normal (is_reversal = false): value > 0 e demais >= 0
--    fluxo de estorno (is_reversal = true): value < 0 e demais <= 0
ALTER TABLE financial_payment_application
    ADD CONSTRAINT ck_payment_application_value_sign CHECK (
        (is_reversal = false AND value > 0)
     OR (is_reversal = true  AND value < 0)
    );

ALTER TABLE financial_payment_application
    ADD CONSTRAINT ck_payment_application_fine_sign CHECK (
        (is_reversal = false AND fine_applied >= 0)
     OR (is_reversal = true  AND fine_applied <= 0)
    );

ALTER TABLE financial_payment_application
    ADD CONSTRAINT ck_payment_application_interest_sign CHECK (
        (is_reversal = false AND interest_applied >= 0)
     OR (is_reversal = true  AND interest_applied <= 0)
    );

ALTER TABLE financial_payment_application
    ADD CONSTRAINT ck_payment_application_discount_sign CHECK (
        (is_reversal = false AND discount_applied >= 0)
     OR (is_reversal = true  AND discount_applied <= 0)
    );
