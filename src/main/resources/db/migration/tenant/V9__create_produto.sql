-- Migração aplicada POR TENANT.
-- Slice 4.1 — Produto foundation: catálogo de produtos com hierarquia
-- product -> variant -> packaging (SKU), atributos dinâmicos, kits e
-- tabelas de preço por filial/vigência.
--
-- Sem stock_* aqui — estoque entra no slice 4.3.
--
-- Doc: docs/domain/12-produto-estoque.md.

-- =====================================================================
-- Catálogo auxiliar de marcas
-- =====================================================================
-- Marca é um catálogo simples (igual aos de cadastros): código + nome,
-- soft delete por tenant. Sem is_system aqui — tenant cadastra suas
-- próprias marcas; não há marcas seedadas globalmente.

CREATE TABLE catalog_brand (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

-- =====================================================================
-- Produto: hierarquia product -> variant -> packaging
-- =====================================================================

CREATE TABLE product (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint NOT NULL DEFAULT 0,
    sku_root        text NOT NULL UNIQUE,
    name            text NOT NULL,
    description     text,
    brand_id        uuid REFERENCES catalog_brand(id),
    classification  text NOT NULL CHECK (classification IN ('PRODUTO','SERVICO','MATERIA_PRIMA','PRODUTO_FINAL')),
    ncm             char(8),
    cest            char(7),
    origin          char(1) NOT NULL DEFAULT '0',
    is_active       boolean NOT NULL DEFAULT true,
    deactivated_at  timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_product_ncm_required_for_goods
        CHECK (classification = 'SERVICO' OR ncm IS NOT NULL)
);
CREATE INDEX idx_product_name ON product (LOWER(name)) WHERE is_active = true;
CREATE INDEX idx_product_ncm  ON product (ncm) WHERE ncm IS NOT NULL;
CREATE INDEX idx_product_brand ON product (brand_id) WHERE brand_id IS NOT NULL;

CREATE TABLE variant (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id      uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    code            text NOT NULL,
    name            text NOT NULL,
    is_default      boolean NOT NULL DEFAULT false,
    is_active       boolean NOT NULL DEFAULT true,
    deactivated_at  timestamptz,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (product_id, code)
);
-- RN-PRD-003: variedade default única por produto.
CREATE UNIQUE INDEX uq_variant_default_per_product
    ON variant (product_id) WHERE is_default = true;

CREATE TABLE packaging (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    variant_id          uuid NOT NULL REFERENCES variant(id) ON DELETE CASCADE,
    sku                 text NOT NULL UNIQUE,
    barcode             text,
    commercial_unit_id  uuid REFERENCES catalog_measurement_unit(id),
    fiscal_unit_id      uuid REFERENCES catalog_measurement_unit(id),
    conversion_factor   numeric(15,4) NOT NULL DEFAULT 1,
    is_default          boolean NOT NULL DEFAULT false,
    is_active           boolean NOT NULL DEFAULT true,
    weight_kg           numeric(10,3),
    deactivated_at      timestamptz,
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_packaging_conversion_positive CHECK (conversion_factor > 0)
);
-- RN-PRD-003: embalagem default única por variedade.
CREATE UNIQUE INDEX uq_packaging_default_per_variant
    ON packaging (variant_id) WHERE is_default = true;
-- RN-PRD-007: barcode único globalmente no tenant quando informado.
CREATE UNIQUE INDEX uq_packaging_barcode
    ON packaging (barcode) WHERE barcode IS NOT NULL;

-- =====================================================================
-- Atributos dinâmicos
-- =====================================================================

CREATE TABLE attribute_template (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    data_type       text NOT NULL CHECK (data_type IN ('TEXT','NUMBER','BOOLEAN','ENUM','DATE')),
    enum_options    jsonb,
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_attribute_enum_options
        CHECK (data_type <> 'ENUM' OR enum_options IS NOT NULL)
);

CREATE TABLE attribute_value (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    template_id     uuid NOT NULL REFERENCES attribute_template(id),
    product_id      uuid REFERENCES product(id) ON DELETE CASCADE,
    variant_id      uuid REFERENCES variant(id) ON DELETE CASCADE,
    value           text NOT NULL,
    created_at      timestamptz NOT NULL DEFAULT now(),
    -- Aplica a product OU variant, nunca aos dois nem a nenhum.
    CONSTRAINT ck_attribute_value_owner
        CHECK ((product_id IS NOT NULL) <> (variant_id IS NOT NULL))
);
CREATE INDEX idx_attribute_value_product ON attribute_value (product_id) WHERE product_id IS NOT NULL;
CREATE INDEX idx_attribute_value_variant ON attribute_value (variant_id) WHERE variant_id IS NOT NULL;
CREATE UNIQUE INDEX uq_attribute_value_product_template
    ON attribute_value (product_id, template_id) WHERE product_id IS NOT NULL;
CREATE UNIQUE INDEX uq_attribute_value_variant_template
    ON attribute_value (variant_id, template_id) WHERE variant_id IS NOT NULL;

-- =====================================================================
-- Kits: composição de packaging por packaging
-- =====================================================================
-- RN-PRD-006: kit explode na venda; saldo é dos componentes, não do kit.

CREATE TABLE product_kit_component (
    id                      uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    kit_packaging_id        uuid NOT NULL REFERENCES packaging(id) ON DELETE CASCADE,
    component_packaging_id  uuid NOT NULL REFERENCES packaging(id),
    quantity                numeric(15,4) NOT NULL,
    created_at              timestamptz NOT NULL DEFAULT now(),
    UNIQUE (kit_packaging_id, component_packaging_id),
    CONSTRAINT ck_kit_no_self_reference
        CHECK (kit_packaging_id <> component_packaging_id),
    CONSTRAINT ck_kit_quantity_positive
        CHECK (quantity > 0)
);
CREATE INDEX idx_kit_component_kit       ON product_kit_component (kit_packaging_id);
CREATE INDEX idx_kit_component_component ON product_kit_component (component_packaging_id);

-- =====================================================================
-- Tabelas de preço: por filial e vigência
-- =====================================================================
-- branch_id NULL = tabela global do tenant (aplica a todas as filiais).

CREATE TABLE price_table (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name            text NOT NULL,
    branch_id       uuid REFERENCES branch(id),
    valid_from      date NOT NULL,
    valid_until     date,
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_price_table_validity
        CHECK (valid_until IS NULL OR valid_until >= valid_from)
);
CREATE INDEX idx_price_table_branch ON price_table (branch_id) WHERE branch_id IS NOT NULL;
CREATE INDEX idx_price_table_validity ON price_table (valid_from, valid_until);

CREATE TABLE price_table_item (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    price_table_id  uuid NOT NULL REFERENCES price_table(id) ON DELETE CASCADE,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    sale_price      numeric(15,2) NOT NULL,
    discount_max    numeric(7,4) NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (price_table_id, packaging_id),
    CONSTRAINT ck_price_item_sale_price_non_negative CHECK (sale_price >= 0),
    CONSTRAINT ck_price_item_discount_range CHECK (discount_max >= 0 AND discount_max <= 1)
);
CREATE INDEX idx_price_item_packaging ON price_table_item (packaging_id);
