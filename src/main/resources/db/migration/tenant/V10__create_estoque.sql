-- Migração aplicada POR TENANT.
-- Slice 4.3 — Estoque foundation: locais físicos dentro da filial, lotes
-- opcionais com validade, snapshot agregado por (packaging,branch,location,batch)
-- e ledger imutável de movimentos.
--
-- Sem services/triggers Java aqui — entram no slice 4.4 com WAC, lock
-- pessimista, reservas, idempotência e port `StockInventoryPort`.
--
-- Doc: docs/domain/12-produto-estoque.md.

-- =====================================================================
-- stock_location: prateleira/setor dentro de uma filial
-- =====================================================================
-- Default por filial (RN-EST-013) é único via index parcial — quando o
-- movimento omite location_id, o caller resolve para o default da filial.

CREATE TABLE stock_location (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    branch_id       uuid NOT NULL REFERENCES branch(id),
    code            text NOT NULL,
    name            text NOT NULL,
    is_default      boolean NOT NULL DEFAULT false,
    is_active       boolean NOT NULL DEFAULT true,
    deactivated_at  timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, code)
);
CREATE UNIQUE INDEX uq_location_default_per_branch
    ON stock_location (branch_id) WHERE is_default = true;
CREATE INDEX idx_stock_location_branch ON stock_location (branch_id);

-- =====================================================================
-- stock_batch: lote opcional com validade
-- =====================================================================
-- RN-EST-011: quando produto exige lote (config futura), movimento sem
-- batch_id deve ser rejeitado. v1 só armazena; enforcement vai no slice 4.4.
-- RN-EST-012: lote vencido emite warning ao ser referenciado (não bloqueia).

CREATE TABLE stock_batch (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    code            text NOT NULL,
    expires_on      date,
    manufactured_on date,
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (packaging_id, code)
);
CREATE INDEX idx_stock_batch_packaging ON stock_batch (packaging_id);
CREATE INDEX idx_stock_batch_expires ON stock_batch (expires_on)
    WHERE expires_on IS NOT NULL;

-- =====================================================================
-- stock_snapshot: saldo agregado por (packaging, branch, location, batch)
-- =====================================================================
-- Materialização do ledger atualizada por trigger Java (RN-EST-003) na
-- mesma transação que insere o movement. Lock pessimista (RN-EST-004)
-- via SELECT ... FOR UPDATE antes de qualquer recálculo.
--
-- quantity em numeric(15,4); WAC em numeric(15,6) para minimizar
-- arredondamento (RN-EST-014); version para optimistic concurrency
-- complementar ao lock pessimista.
--
-- batch_id null = "sem lote" — UNIQUE precisa cobrir esse caso, então
-- usamos COALESCE no índice (NULL nunca == NULL no UNIQUE padrão).

CREATE TABLE stock_snapshot (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    packaging_id        uuid NOT NULL REFERENCES packaging(id),
    branch_id           uuid NOT NULL REFERENCES branch(id),
    location_id         uuid NOT NULL REFERENCES stock_location(id),
    batch_id            uuid REFERENCES stock_batch(id),
    quantity_on_hand    numeric(15,4) NOT NULL DEFAULT 0,
    quantity_reserved   numeric(15,4) NOT NULL DEFAULT 0,
    weighted_avg_cost   numeric(15,6) NOT NULL DEFAULT 0,
    version             bigint NOT NULL DEFAULT 0,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_snapshot_qty_non_negative
        CHECK (quantity_on_hand >= 0 AND quantity_reserved >= 0),
    CONSTRAINT ck_snapshot_wac_non_negative
        CHECK (weighted_avg_cost >= 0),
    CONSTRAINT ck_snapshot_reserved_le_on_hand
        CHECK (quantity_reserved <= quantity_on_hand)
);
-- UNIQUE incluindo batch_id NULL: cobre o caso "sem lote" tratando NULL
-- como uma "categoria" determinística via COALESCE no índice.
CREATE UNIQUE INDEX uq_stock_snapshot_key
    ON stock_snapshot (packaging_id, branch_id, location_id, COALESCE(batch_id, '00000000-0000-0000-0000-000000000000'::uuid));
CREATE INDEX idx_stock_snapshot_packaging ON stock_snapshot (packaging_id);
CREATE INDEX idx_stock_snapshot_branch    ON stock_snapshot (branch_id);
CREATE INDEX idx_stock_snapshot_batch     ON stock_snapshot (batch_id)
    WHERE batch_id IS NOT NULL;

-- =====================================================================
-- stock_movement: ledger imutável de movimentos
-- =====================================================================
-- RN-EST-002: append-only — não há UPDATE/DELETE. Correção via REVERSAL
-- ou ADJUSTMENT, nunca alterando linhas existentes.
-- RN-EST-005: idempotência por chave natural, formato sugerido
-- `<origin_type>:<origin_id>:<sub_seq>`. Tentativa duplicada retorna o
-- movimento existente sem efeito colateral.
-- RN-EST-008: TRANSFER usa par TRANSFER_OUT + TRANSFER_IN com mesmo
-- origin_id (transfer_id).
-- RN-EST-009: REVERSAL aponta para o movimento original em reason via
-- prefixo `REVERSAL_OF:`.

CREATE TABLE stock_movement (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    direction       text NOT NULL CHECK (direction IN
        ('INBOUND','OUTBOUND','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT','REVERSAL')),
    reason          text NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    branch_id       uuid NOT NULL REFERENCES branch(id),
    location_id     uuid NOT NULL REFERENCES stock_location(id),
    batch_id        uuid REFERENCES stock_batch(id),
    quantity        numeric(15,4) NOT NULL,
    unit_cost       numeric(15,6),
    total_cost      numeric(15,6),
    origin_type     text,
    origin_id       uuid,
    idempotency_key text,
    user_id         uuid,
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_movement_quantity_positive CHECK (quantity > 0),
    CONSTRAINT ck_movement_unit_cost_non_negative
        CHECK (unit_cost IS NULL OR unit_cost >= 0),
    CONSTRAINT ck_movement_total_cost_non_negative
        CHECK (total_cost IS NULL OR total_cost >= 0)
);
-- Idempotency key único quando informado (RN-EST-005).
CREATE UNIQUE INDEX uq_movement_idempotency
    ON stock_movement (idempotency_key) WHERE idempotency_key IS NOT NULL;
-- Index para consulta de ledger paginada por SKU+filial ordenada
-- desc — RN-EST não-funcional (p95 consulta < 30ms).
CREATE INDEX idx_movement_packaging_branch_time
    ON stock_movement (packaging_id, branch_id, occurred_at DESC);
-- Index para rastreio cross-module: "todos movimentos da venda X".
CREATE INDEX idx_movement_origin
    ON stock_movement (origin_type, origin_id)
    WHERE origin_type IS NOT NULL;
