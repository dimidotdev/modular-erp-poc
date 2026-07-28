# 12 — Produto e Estoque

Dois bounded contexts irmãos: `produto` (catálogo) e `estoque` (saldo e movimentos). Documentados juntos porque sua interação é íntima e a maioria dos casos de uso atravessa os dois.

## Escopo

### Produto

**Cobre**: hierarquia **produto → variedade → embalagem (SKU)**, NCM/CEST, marca, classificação tributária, atributos dinâmicos, kits (composição), tabelas de preço por vigência, código de barras (EAN/GTIN).

**Não cobre v1**: configurador de produto baseado em atributos (escolhe cor+tamanho+voltagem → SKU), promoções/campanhas, recomendação.

### Estoque

**Cobre**: saldo por SKU + filial + local, ledger imutável de movimentos (INBOUND/OUTBOUND/TRANSFER/ADJUSTMENT/REVERSAL), custo médio ponderado (WAC), lotes com validade, locais físicos dentro da filial, idempotência por chave natural, lock pessimista, reservas (saldo virtual).

**Não cobre v1**: FIFO/LIFO, inventário cíclico automatizado, WMS endereçamento avançado, picking, kit explosion automática.

## Conceitos centrais

### Produto

- **Produto (`product`)**: nível mais alto da hierarquia. "Camiseta polo". Tem nome, descrição, marca, NCM, CEST, classificação, atributos.
- **Variedade (`variant`)**: variação do produto. "Camiseta polo azul M". 0..N por produto. Se produto não tem variedades, há uma variedade default implícita.
- **Embalagem (`packaging`)**: **SKU final** que vende/movimenta/factura. "Camiseta polo azul M, caixa com 5". Tem código de barras, unidade comercial, fator de conversão para unidade base. 1..N por variedade.

> **A embalagem é a unidade que NFe, venda, compra e estoque enxergam.** O `embalagem_id` (mais comumente chamado de `sku_id` no domínio) é o que viaja entre módulos.

- **Atributo (`attribute_template` + `attribute_value`)**: pares chave-valor dinâmicos vinculados a produto ou variedade. Configuráveis por tenant.
- **Kit (`product_kit_component`)**: composição. Produto kit = soma de outros produtos. Estoque consumido na **explosão** durante venda (não há produto kit em saldo).
- **Tabela de preço (`price_table` + `price_table_item`)**: por filial e vigência. Item: preço de venda da embalagem.

### Estoque

- **Saldo (`stock_snapshot`)**: tupla `(packaging_id, branch_id, location_id, batch_id)` com `quantity_on_hand`, `quantity_reserved`, `weighted_avg_cost`, `version`. Materialização agregada do ledger.
- **Ledger (`stock_movement`)**: registro imutável de movimento. Append-only. Cada linha contém: chave natural (idempotência), sentido, qtd, custo unitário, motivo, referência à origem (sale_id, purchase_id, etc).
- **Local (`stock_location`)**: prateleira/setor dentro da filial. Default: 1 local "Padrão" por filial.
- **Lote (`stock_batch`)**: opcional. Identificado por código + validade. Saldos podem ser por lote.
- **Reserva**: `quantity_reserved` é incrementada quando pedido em aberto/contrato gera demanda futura; deduzida do disponível. Movimento real ainda não ocorreu.
- **Custo médio ponderado (WAC)**: recalculado a cada INBOUND com custo. Fórmula:
  ```
  new_wac = ((qtd_atual × wac_atual) + (qtd_entrada × custo_entrada)) / (qtd_atual + qtd_entrada)
  ```

## Modelo de dados

### Produto

```sql
CREATE TABLE product (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    sku_root        text NOT NULL UNIQUE,        -- "POLO-AZ" (prefixo do tenant)
    name            text NOT NULL,
    description     text,
    brand_id        uuid REFERENCES catalog_brand(id),
    classification  text CHECK (classification IN ('PRODUTO','SERVICO','MATERIA_PRIMA','PRODUTO_FINAL')),
    ncm             char(8),                      -- obrigatório para PRODUTO
    cest            char(7),
    origin          char(1) NOT NULL DEFAULT '0',  -- origem da mercadoria (NFe)
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_product_name ON product(LOWER(name)) WHERE is_active = true;
CREATE INDEX idx_product_ncm ON product(ncm) WHERE ncm IS NOT NULL;

CREATE TABLE variant (
    id              uuid PRIMARY KEY,
    product_id      uuid NOT NULL REFERENCES product(id) ON DELETE CASCADE,
    code            text NOT NULL,                -- "AZ-M", "VM-G"
    name            text NOT NULL,                -- "Azul M"
    is_default      boolean NOT NULL DEFAULT false,
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (product_id, code)
);
CREATE UNIQUE INDEX uq_variant_default ON variant(product_id) WHERE is_default = true;

CREATE TABLE packaging (
    id                  uuid PRIMARY KEY,
    variant_id          uuid NOT NULL REFERENCES variant(id) ON DELETE CASCADE,
    sku                 text NOT NULL UNIQUE,     -- "POLO-AZ-M-CX5"
    barcode             text,                     -- EAN-13 ou outro
    commercial_unit_id  uuid REFERENCES catalog_measurement_unit(id),
    fiscal_unit_id      uuid REFERENCES catalog_measurement_unit(id),
    conversion_factor   numeric(15,4) NOT NULL DEFAULT 1,  -- qtd em unidade base
    is_default          boolean NOT NULL DEFAULT false,
    is_active           boolean NOT NULL DEFAULT true,
    weight_kg           numeric(10,3),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_packaging_default ON packaging(variant_id) WHERE is_default = true;
CREATE INDEX idx_packaging_barcode ON packaging(barcode) WHERE barcode IS NOT NULL;

CREATE TABLE attribute_template (
    id              uuid PRIMARY KEY,
    code            text NOT NULL UNIQUE,         -- "COLOR", "SIZE"
    name            text NOT NULL,
    data_type       text NOT NULL CHECK (data_type IN ('TEXT','NUMBER','BOOLEAN','ENUM','DATE')),
    enum_options    jsonb,                         -- ["XS","S","M","L","XL"] quando ENUM
    is_active       boolean NOT NULL DEFAULT true
);

CREATE TABLE attribute_value (
    id              uuid PRIMARY KEY,
    template_id     uuid NOT NULL REFERENCES attribute_template(id),
    product_id      uuid REFERENCES product(id) ON DELETE CASCADE,
    variant_id      uuid REFERENCES variant(id) ON DELETE CASCADE,
    value           text NOT NULL,
    CHECK ( (product_id IS NOT NULL) <> (variant_id IS NOT NULL) )
);

CREATE TABLE product_kit_component (
    kit_packaging_id    uuid NOT NULL REFERENCES packaging(id) ON DELETE CASCADE,
    component_packaging_id uuid NOT NULL REFERENCES packaging(id),
    quantity            numeric(15,4) NOT NULL,
    PRIMARY KEY (kit_packaging_id, component_packaging_id),
    CHECK (kit_packaging_id <> component_packaging_id)
);

CREATE TABLE price_table (
    id              uuid PRIMARY KEY,
    name            text NOT NULL,
    branch_id       uuid REFERENCES branch(id),    -- nulo = todas as filiais
    valid_from      date NOT NULL,
    valid_until     date,                          -- aberto
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE price_table_item (
    id              uuid PRIMARY KEY,
    price_table_id  uuid NOT NULL REFERENCES price_table(id) ON DELETE CASCADE,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    sale_price      numeric(15,2) NOT NULL,
    discount_max    numeric(7,4) NOT NULL DEFAULT 0,   -- 0.1000 = 10%
    UNIQUE (price_table_id, packaging_id)
);
```

### Estoque

```sql
CREATE TABLE stock_location (
    id              uuid PRIMARY KEY,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    code            text NOT NULL,
    name            text NOT NULL,
    is_default      boolean NOT NULL DEFAULT false,
    is_active       boolean NOT NULL DEFAULT true,
    UNIQUE (branch_id, code)
);
CREATE UNIQUE INDEX uq_location_default_per_branch
    ON stock_location(branch_id) WHERE is_default = true;

CREATE TABLE stock_batch (
    id              uuid PRIMARY KEY,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    code            text NOT NULL,
    expires_on      date,
    manufactured_on date,
    notes           text,
    UNIQUE (packaging_id, code)
);

CREATE TABLE stock_snapshot (
    id                  uuid PRIMARY KEY,
    packaging_id        uuid NOT NULL REFERENCES packaging(id),
    branch_id           uuid NOT NULL REFERENCES branch(id),
    location_id         uuid NOT NULL REFERENCES stock_location(id),
    batch_id            uuid REFERENCES stock_batch(id),
    quantity_on_hand    numeric(15,4) NOT NULL DEFAULT 0,
    quantity_reserved   numeric(15,4) NOT NULL DEFAULT 0,
    weighted_avg_cost   numeric(15,6) NOT NULL DEFAULT 0,
    version             bigint NOT NULL DEFAULT 0,
    updated_at          timestamptz NOT NULL DEFAULT now(),
    UNIQUE (packaging_id, branch_id, location_id, batch_id)
);
CREATE INDEX idx_stock_snapshot_packaging ON stock_snapshot(packaging_id);
CREATE INDEX idx_stock_snapshot_branch    ON stock_snapshot(branch_id);

CREATE TABLE stock_movement (
    id              uuid PRIMARY KEY,
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    direction       text NOT NULL CHECK (direction IN ('INBOUND','OUTBOUND','TRANSFER_OUT','TRANSFER_IN','ADJUSTMENT','REVERSAL')),
    reason          text NOT NULL,                 -- 'PURCHASE','SALE','RETURN','TRANSFER','INVENTORY','REVERSAL_OF:<id>'
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    branch_id       uuid NOT NULL REFERENCES branch(id),
    location_id     uuid NOT NULL REFERENCES stock_location(id),
    batch_id        uuid REFERENCES stock_batch(id),
    quantity        numeric(15,4) NOT NULL,         -- sempre positivo; direction define sentido
    unit_cost       numeric(15,6),                  -- relevante para INBOUND e cálculos
    total_cost      numeric(15,6),
    origin_type     text,                           -- 'sale','purchase','transfer','adjustment','os'
    origin_id       uuid,                           -- referência cross-module
    idempotency_key text,                            -- chave natural
    user_id         uuid,
    notes           text,
    UNIQUE (idempotency_key) WHERE idempotency_key IS NOT NULL
);
CREATE INDEX idx_movement_packaging_branch_time
    ON stock_movement(packaging_id, branch_id, occurred_at DESC);
CREATE INDEX idx_movement_origin
    ON stock_movement(origin_type, origin_id);
```

## Estrutura Java

### `produto`

```
dev.dimi.erp.produto
├── package-info.java        ← allowedDependencies={"cadastros","shared"}
├── PackagingId.java, ProductId.java
├── ProductQueryPort.java    ← exposto a outros módulos
├── PriceLookupPort.java
├── eventos:
│   ├── ProductCreatedEvent.java
│   ├── PackagingCreatedEvent.java
│   ├── PackagingDeactivatedEvent.java
│   └── PriceTableUpdatedEvent.java
└── internal/
    ├── domain/        Product, Variant, Packaging, AttributeTemplate, Kit, PriceTable
    ├── persistence/   *Repository
    ├── application/   ManageProductService, ManagePackagingService, ManagePriceService
    └── web/           ProductController, PackagingController, PriceTableController
```

### `estoque`

```
dev.dimi.erp.estoque
├── package-info.java         ← allowedDependencies={"produto","cadastros","shared"}
├── PackagingStockId, MovementId
├── StockInventoryPort.java   ← consumido por movimentação/compras/OS
│   ├── reserve(packagingId, branchId, qty, originRef)
│   ├── unreserve(...)
│   ├── ship(...)             ← consome reserva e move OUTBOUND
│   ├── receive(...)          ← INBOUND com custo
│   ├── transfer(...)
│   ├── adjust(...)
│   └── reverse(movementId, reason)
├── StockQueryPort.java
├── eventos:
│   ├── StockMovementRecordedEvent.java
│   ├── StockReservedEvent.java
│   ├── StockReservationReleasedEvent.java
│   ├── StockLowAlertEvent.java
│   └── StockAdjustmentRecordedEvent.java
└── internal/
    ├── domain/        StockSnapshot, StockMovement, StockBatch, StockLocation
    ├── persistence/   *Repository
    ├── application/   RecordMovementService, ReserveStockService, RecomputeWacService
    └── web/           StockController, StockMovementController, LocationController, BatchController
```

## Casos de uso e endpoints

### Produto

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/produtos` | `produto.consultar` |
| `POST` | `/api/v1/produtos` | `produto.criar` |
| `GET`  | `/api/v1/produtos/{id}` | `produto.consultar` |
| `PATCH`| `/api/v1/produtos/{id}` | `produto.editar` |
| `DELETE`| `/api/v1/produtos/{id}` | `produto.editar` (soft) |
| `POST` | `/api/v1/produtos/{id}/variants` | `produto.editar` |
| `POST` | `/api/v1/produtos/{id}/variants/{variantId}/packagings` | `produto.editar` |
| `PATCH`| `/api/v1/produtos/packagings/{packagingId}` | `produto.editar` |
| `POST` | `/api/v1/produtos/packagings/{packagingId}/kit-components` | `produto.editar` |
| `GET`  | `/api/v1/produtos/search?term=...&barcode=...` | `produto.consultar` (busca rápida para vendas) |

### Tabela de preço

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/produtos/price-tables` | `produto.preco.consultar` |
| `POST` | `/api/v1/produtos/price-tables` | `produto.preco.alterar` |
| `PUT`  | `/api/v1/produtos/price-tables/{id}/items` | `produto.preco.alterar` (batch upsert) |

### Estoque

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/estoque/snapshots?packagingId=...&branchId=...` | `estoque.consultar` |
| `GET`  | `/api/v1/estoque/movements?packagingId=...&branchId=...&from=...&to=...` | `estoque.consultar` (paginação cursor) |
| `GET`  | `/api/v1/estoque/movements/{id}` | `estoque.consultar` |
| `POST` | `/api/v1/estoque/adjustments` | `estoque.ajustar` + step-up (motivo obrigatório) |
| `POST` | `/api/v1/estoque/transfers` | `estoque.transferir` |
| `POST` | `/api/v1/estoque/movements/{id}/reverse` | `estoque.ajustar` + step-up |
| `GET`  | `/api/v1/estoque/locations?branchId=...` | `estoque.consultar` |
| `POST` | `/api/v1/estoque/locations` | `estoque.editar` |
| `GET`  | `/api/v1/estoque/batches?packagingId=...` | `estoque.consultar` |
| `POST` | `/api/v1/estoque/batches` | `estoque.editar` |

## Eventos

```java
// produto
public record ProductCreatedEvent(ProductId productId, TenantId tenantId, String name, Instant occurredAt) {}
public record PackagingCreatedEvent(PackagingId packagingId, ProductId productId, String sku, Instant occurredAt) {}
public record PackagingDeactivatedEvent(PackagingId packagingId, TenantId tenantId, Instant occurredAt) {}
public record PriceTableUpdatedEvent(PriceTableId id, TenantId tenantId, BranchId branchId, Instant occurredAt) {}

// estoque
public record StockMovementRecordedEvent(MovementId movementId, PackagingId packagingId, BranchId branchId,
    Direction direction, BigDecimal quantity, BigDecimal unitCost, String originType, UUID originId, Instant occurredAt) {}
public record StockReservedEvent(PackagingId packagingId, BranchId branchId, BigDecimal quantity, String originType, UUID originId, Instant occurredAt) {}
public record StockReservationReleasedEvent(PackagingId packagingId, BranchId branchId, BigDecimal quantity, String originType, UUID originId, Instant occurredAt) {}
public record StockLowAlertEvent(PackagingId packagingId, BranchId branchId, BigDecimal quantityOnHand, BigDecimal threshold, Instant occurredAt) {}
public record StockAdjustmentRecordedEvent(MovementId movementId, BigDecimal delta, String reason, UserId by, Instant occurredAt) {}
```

Consumidores:

- **`movimentacao` / `compras` / `ordem-servico`**: usam `StockInventoryPort` (síncrono) para operar estoque na mesma transação ou em listener.
- **`auditoria`**: tudo.
- **`fiscal`**: opcional — recomputar custo para informar em NFe.

## Regras de negócio

### Produto

- **RN-PRD-001**: SKU é a **embalagem (`packaging`)**, não o produto. Venda, NFe, compra, estoque trabalham com `packaging_id`.
- **RN-PRD-002**: NCM obrigatório para `classification IN ('PRODUTO','PRODUTO_FINAL','MATERIA_PRIMA')`.
- **RN-PRD-003**: Variedade default única por produto; embalagem default única por variedade. Reflete a opção "sem variedade explícita" como uma variedade default invisível.
- **RN-PRD-004**: Preço autoritativo é `price_table_item.sale_price` válido para `(branch, packaging, date)`. Sem tabela cobrindo → erro `product.no-price`.
- **RN-PRD-005**: Desconto máximo por embalagem (`discount_max`) é enforced no momento da venda em `movimentacao`.
- **RN-PRD-006**: Kit explode na venda: cada componente vira linha de movimento; estoque consumido por componente. Kit não tem saldo próprio.
- **RN-PRD-007**: Código de barras (`barcode`) é único globalmente no tenant quando informado (índice parcial).
- **RN-PRD-008**: Soft delete em produto/variedade/embalagem. Não pode reativar variedade/embalagem cujo produto está inativo.

### Estoque

- **RN-EST-001**: Custo médio ponderado (WAC), sem FIFO/LIFO.
- **RN-EST-002**: `stock_movement` é **imutável** após criação. Não há UPDATE/DELETE. Correção via estorno (REVERSAL) ou ajuste.
- **RN-EST-003**: Snapshot é atualizado por trigger Java (não por SQL trigger) na mesma transação do INSERT do movement.
- **RN-EST-004**: Lock pessimista (`SELECT ... FOR UPDATE`) no `stock_snapshot` antes de qualquer recálculo. Garante consistência sob concorrência.
- **RN-EST-005**: Idempotência por **chave natural** (`idempotency_key`) — recomendado formato `<origin_type>:<origin_id>:<sub_seq>`. Tentativa duplicada retorna o movimento existente sem efeito.
- **RN-EST-006**: Estoque negativo só se `tenant_setting.allow_negative_stock=true`. Default: false. Bloqueia OUTBOUND que levaria a saldo < 0 retornando `409` com `code=stock.insufficient`.
- **RN-EST-007**: Reserva é **número virtual** — `quantity_available = quantity_on_hand - quantity_reserved`. OUTBOUND que efetiva uma reserva decrementa ambos.
- **RN-EST-008**: TRANSFER entre filiais é par atômico de movimentos (`TRANSFER_OUT` na origem + `TRANSFER_IN` no destino), com **mesmo `transfer_id`** como `origin_id`. Falha em qualquer um → rollback total.
- **RN-EST-009**: REVERSAL é movimento inverso da chave natural original; aponta para o movimento original em `reason='REVERSAL_OF:<id>'`. NÃO recalcula WAC retroativamente, por decisão explícita de escopo.
- **RN-EST-010**: Ajuste manual (`ADJUSTMENT`) exige motivo + step-up + permission `estoque.ajustar`. Auditoria reforçada.
- **RN-EST-011**: Lotes opcionais — quando obrigatório (config por produto), movimento sem `batch_id` é rejeitado.
- **RN-EST-012**: Lote com validade passada não pode ser referenciado em OUTBOUND ou nova INBOUND (warning, não bloqueio, configurável).
- **RN-EST-013**: Local default por filial — quando movimento omite `location_id`, usa o default da filial.
- **RN-EST-014**: WAC armazenado em `numeric(15,6)` (6 casas) para minimizar erro de arredondamento. Apresentado em 2 casas.
- **RN-EST-015**: Alerta de estoque baixo (`StockLowAlertEvent`) emitido quando `quantity_on_hand < (config.threshold)` por produto/filial — config futura, v1 emite com threshold fixo.

## Integrações

- **Módulo `cadastros`**: produto referencia `branch` (filial), `catalog_brand`, `catalog_measurement_unit`. Estoque referencia `branch`. Pessoa não.
- **Módulo `compras`**: chama `StockInventoryPort.receive()` no recebimento; INBOUND inline com custo de aquisição.
- **Módulo `movimentacao`**: chama `StockInventoryPort.reserve()` no pedido, `ship()` na venda fechada; OUTBOUND.
- **Módulo `ordem-servico`**: consome estoque dos materiais usados na OS.
- **Módulo `fiscal`**: NFe inclui dados de produto via `ProductQueryPort` (NCM, CEST, origem, unidade fiscal).
- **Módulo `auditoria`**: todos os eventos publicados.
- **Módulo `contratos`**: futuro v1.1 — kits de locação reservam estoque ao iniciar contrato.

## Critérios de aceitação

- **Dado** um produto sem variedade explícita, **quando** criado, **então** automaticamente são criadas uma variedade default e uma embalagem default (1:1:1).
- **Dado** dois pedidos paralelos para o mesmo SKU/filial com saldo 1, **quando** ambos tentam reservar 1, **então** apenas o primeiro tem sucesso; o segundo retorna `409 stock.insufficient` (lock pessimista).
- **Dado** uma compra com 10 unidades a R$ 100 cada (qtd_atual=0), **quando** INBOUND processa, **então** snapshot fica com 10 + WAC=100.000000.
- **Dado** estoque com 10 unidades WAC=100, **quando** nova INBOUND de 5 unidades a R$ 120, **então** WAC = (10×100 + 5×120) / 15 = 106.666666.
- **Dado** um INBOUND, **quando** uma REVERSAL é criada, **então** snapshot tem qtd reduzida em qtd reversed, e WAC mantém o valor anterior (não recalcula).
- **Dado** uma TRANSFER de A→B com 5 unidades, **quando** a parte de origem grava OK mas destino falha, **então** transação inteira é revertida.
- **Dado** uma venda com kit, **quando** fechada, **então** N OUTBOUNDs (um por componente) são gerados na mesma transação.
- **Dado** uma idempotency key já usada, **quando** novo POST com mesma key, **então** retorna o movimento original (não duplica).

## Não-funcionais

- **Latência reserve/ship/receive**: p95 < 80ms (lock pessimista local).
- **Latência consulta snapshot**: p95 < 30ms.
- **Concorrência**: 100 movimentos/seg em SKU populares sem starvation (lock segura, transações curtas).
- **Volume**: 50M+ movements em 5 anos — particionar tabela `stock_movement` por mês quando atingir 10M.
- **Snapshot drift**: snapshot e ledger nunca divergem (recálculo total não é parte do v1; aceito por design).

## Roadmap

- **v1.1**: inventário cíclico assistido (selecionar itens, comparar com físico, gerar ajustes); reservas automáticas em pedidos de venda em aberto; alertas configuráveis por SKU.
- **v1.2**: FIFO/LIFO por configuração; WMS endereçamento real (posição: rua-prateleira-nível-coluna); picking guiado.
- **v2**: configurador de produto (atributos → SKU); planejamento (MRP simples); recomendação de compra automática.
