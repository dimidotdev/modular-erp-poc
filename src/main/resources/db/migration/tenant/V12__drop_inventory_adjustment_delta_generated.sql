-- V12 — converte stock_inventory_adjustment_item.delta de GENERATED ALWAYS
-- para coluna regular preservando dados existentes.
--
-- Motivação: Hibernate não recupera valores de colunas GENERATED ALWAYS STORED
-- após INSERT de forma confiável (mesmo com @Generated(event = INSERT) —
-- a primeira fetch via persistence context serve o entity inserted ainda
-- sem o valor calculado). Resultado: getter retorna null, API retorna campo
-- ausente.
--
-- Fix: serviço calcula delta em Java (counted - expected) e persiste. Coluna
-- vira regular não-nula. Validação semântica permanece no service.
--
-- Postgres 12+: DROP EXPRESSION preserva dados.

ALTER TABLE stock_inventory_adjustment_item ALTER COLUMN delta DROP EXPRESSION;
