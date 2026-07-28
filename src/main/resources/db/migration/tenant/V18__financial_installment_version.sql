-- Migração aplicada POR TENANT.
-- Audit-3: optimistic locking em financial_installment.
--
-- Sem @Version, dois batches concorrentes pagando a mesma parcela podiam
-- gerar lost-update: cada thread lê paid_value=0, cada uma soma seu delta,
-- last-write-wins → dinheiro silenciosamente desaparece sem erro logado.
-- @Version + retry on OptimisticLockingFailureException no service fecha
-- a brecha (mesmo padrão de FinancialRecord e FinancialPayment, que já
-- têm @Version desde a foundation).
--
-- Doc: docs/domain/17-financeiro.md, RN-FIN-009.

ALTER TABLE financial_installment
    ADD COLUMN version bigint NOT NULL DEFAULT 0;
