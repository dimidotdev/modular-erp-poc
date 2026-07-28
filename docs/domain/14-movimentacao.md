# 14 — Movimentação Comercial

Módulo `movimentacao` — fluxo operacional de **saída** de mercadorias: pedido, venda, devolução, transferência interna, saída avulsa, ajuste de inventário.

## Escopo

**Cobre**: pedido de venda (intenção, sem efeito em estoque/financeiro), venda (baixa estoque + gera MVF), devolução de venda (cliente → empresa), transferência entre filiais/locais (interna), saída avulsa (amostra, brinde, garantia), ajuste de inventário (correção sem origem comercial), cancelamento de venda como Saga.

**Não cobre v1**: PDV/Caixa físico (cortado do escopo), TEF/PIX maquininha (v1.1+ se necessário), promoções automáticas, programa de fidelidade.

## Conceitos centrais

- **Pedido (`sales_order`)**: intenção de venda. Não baixa estoque, não gera MVF. Tem itens, condição de pagamento, validade. Pode ser convertido em venda quando aprovado.
- **Venda (`sale`)**: documento de saída efetiva. Baixa estoque (via `StockInventoryPort.ship()`), gera MVF a receber, opcionalmente dispara emissão de NFe.
- **Devolução de venda (`sale_return`)**: parcial ou total. Devolve mercadoria ao estoque, estorna MVF correspondente, emite NFe de devolução. Tem 3 quantidades: devolvida (entra estoque), perdida (não entra), utilizada (consumida pelo cliente, não retorna).
- **Transferência (`stock_transfer`)**: par atômico OUTBOUND→INBOUND entre filiais ou locais. Quando entre filiais com CNPJs distintos, gera NFe.
- **Saída avulsa (`outbound_voucher`)**: amostra, brinde, garantia, perda. Reduz estoque com CFOP apropriado, sem cliente típico.
- **Ajuste de inventário (`stock_inventory_adjustment`)**: correção pontual de saldo (sobra/falta detectada em conferência), com motivo + step-up.

## Modelo de dados

```sql
CREATE TABLE sales_order (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    customer_id     uuid NOT NULL REFERENCES person(id),
    salesperson_id  uuid REFERENCES person(id),
    payment_condition_id uuid REFERENCES catalog_payment_condition(id),
    price_table_id  uuid REFERENCES price_table(id),
    number          bigint NOT NULL,
    status          text NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','CONVERTED','CANCELLED','EXPIRED')),
    valid_until     date,
    total_gross     numeric(15,2) NOT NULL DEFAULT 0,
    total_discount  numeric(15,2) NOT NULL DEFAULT 0,
    total           numeric(15,2) NOT NULL DEFAULT 0,
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    converted_at    timestamptz,
    converted_sale_id uuid,
    UNIQUE (branch_id, number)
);

CREATE TABLE sales_order_item (
    id              uuid PRIMARY KEY,
    order_id        uuid NOT NULL REFERENCES sales_order(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15,4) NOT NULL,
    unit_price      numeric(15,4) NOT NULL,
    discount_pct    numeric(7,4) NOT NULL DEFAULT 0,
    discount_value  numeric(15,2) NOT NULL DEFAULT 0,
    net_value       numeric(15,2) NOT NULL,
    UNIQUE (order_id, line_number)
);

CREATE TABLE sale (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    customer_id     uuid NOT NULL REFERENCES person(id),
    salesperson_id  uuid REFERENCES person(id),
    sales_order_id  uuid REFERENCES sales_order(id),
    payment_condition_id uuid REFERENCES catalog_payment_condition(id),
    number          bigint NOT NULL,
    status          text NOT NULL CHECK (status IN ('OPEN','CLOSED','CANCELLED','PARTIAL_CANCELLATION_FAILED')),
    issued_at       timestamptz NOT NULL DEFAULT now(),
    closed_at       timestamptz,
    cancelled_at    timestamptz,
    cancellation_reason text,
    total_gross     numeric(15,2) NOT NULL DEFAULT 0,
    total_discount  numeric(15,2) NOT NULL DEFAULT 0,
    total_freight   numeric(15,2) NOT NULL DEFAULT 0,
    total           numeric(15,2) NOT NULL DEFAULT 0,
    invoice_id      uuid REFERENCES invoice(id),
    notes           text,
    idempotency_key text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number),
    UNIQUE (idempotency_key) WHERE idempotency_key IS NOT NULL
);
CREATE INDEX idx_sale_status_date ON sale(status, issued_at DESC);

CREATE TABLE sale_item (
    id              uuid PRIMARY KEY,
    sale_id         uuid NOT NULL REFERENCES sale(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15,4) NOT NULL,
    unit_price      numeric(15,4) NOT NULL,
    discount_value  numeric(15,2) NOT NULL DEFAULT 0,
    net_value       numeric(15,2) NOT NULL,
    cfop            char(4),
    UNIQUE (sale_id, line_number)
);

CREATE TABLE sale_return (
    id              uuid PRIMARY KEY,
    sale_id         uuid NOT NULL REFERENCES sale(id),
    branch_id       uuid NOT NULL REFERENCES branch(id),
    status          text NOT NULL CHECK (status IN ('OPEN','CLOSED','CANCELLED')),
    issued_at       timestamptz NOT NULL DEFAULT now(),
    reason          text NOT NULL,
    total           numeric(15,2) NOT NULL,
    invoice_id      uuid REFERENCES invoice(id),
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE sale_return_item (
    id              uuid PRIMARY KEY,
    return_id       uuid NOT NULL REFERENCES sale_return(id) ON DELETE CASCADE,
    sale_item_id    uuid NOT NULL REFERENCES sale_item(id),
    quantity_returned numeric(15,4) NOT NULL DEFAULT 0,
    quantity_lost     numeric(15,4) NOT NULL DEFAULT 0,
    quantity_used     numeric(15,4) NOT NULL DEFAULT 0,
    refund_value      numeric(15,2) NOT NULL,
    CHECK (quantity_returned + quantity_lost + quantity_used <= (SELECT quantity FROM sale_item WHERE id = sale_item_id))
);

CREATE TABLE stock_transfer (
    id              uuid PRIMARY KEY,
    number          bigint NOT NULL,
    origin_branch_id  uuid NOT NULL REFERENCES branch(id),
    dest_branch_id    uuid NOT NULL REFERENCES branch(id),
    origin_location_id uuid REFERENCES stock_location(id),
    dest_location_id   uuid REFERENCES stock_location(id),
    status          text NOT NULL CHECK (status IN ('OPEN','CLOSED','CANCELLED')),
    issued_at       timestamptz NOT NULL DEFAULT now(),
    closed_at       timestamptz,
    invoice_id      uuid REFERENCES invoice(id),    -- quando entre filiais com CNPJ distintos
    notes           text,
    UNIQUE (origin_branch_id, number)
);

CREATE TABLE stock_transfer_item (
    id              uuid PRIMARY KEY,
    transfer_id     uuid NOT NULL REFERENCES stock_transfer(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15,4) NOT NULL,
    UNIQUE (transfer_id, line_number)
);

CREATE TABLE outbound_voucher (
    id              uuid PRIMARY KEY,
    number          bigint NOT NULL,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    reason          text NOT NULL CHECK (reason IN ('SAMPLE','GIFT','WARRANTY','LOSS','OTHER')),
    issued_at       timestamptz NOT NULL DEFAULT now(),
    recipient_person_id uuid REFERENCES person(id),  -- opcional
    notes           text,
    UNIQUE (branch_id, number)
);

CREATE TABLE outbound_voucher_item (
    id              uuid PRIMARY KEY,
    voucher_id      uuid NOT NULL REFERENCES outbound_voucher(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15,4) NOT NULL,
    UNIQUE (voucher_id, line_number)
);

CREATE TABLE stock_inventory_adjustment (
    id              uuid PRIMARY KEY,
    number          bigint NOT NULL,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    issued_at       timestamptz NOT NULL DEFAULT now(),
    reason          text NOT NULL,
    notes           text,
    user_id         uuid NOT NULL,                  -- responsável
    UNIQUE (branch_id, number)
);

CREATE TABLE stock_inventory_adjustment_item (
    id              uuid PRIMARY KEY,
    adjustment_id   uuid NOT NULL REFERENCES stock_inventory_adjustment(id) ON DELETE CASCADE,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    location_id     uuid NOT NULL REFERENCES stock_location(id),
    expected_qty    numeric(15,4) NOT NULL,
    counted_qty     numeric(15,4) NOT NULL,
    delta           numeric(15,4) GENERATED ALWAYS AS (counted_qty - expected_qty) STORED
);
```

## Estrutura Java

```
dev.dimi.erp.movimentacao
├── package-info.java   ← allowedDependencies={"cadastros","produto","estoque","fiscal","financeiro","shared"}
├── SaleId, SalesOrderId, ReturnId, TransferId
├── eventos:
│   ├── SalesOrderCreatedEvent, SalesOrderApprovedEvent, SalesOrderConvertedEvent
│   ├── SaleOpenedEvent, SaleClosedEvent, SaleCancellationRequestedEvent,
│   ├── SaleCancellationCompletedEvent, SaleCancellationFailedEvent
│   ├── SaleReturnOpenedEvent, SaleReturnClosedEvent
│   ├── StockTransferOpenedEvent, StockTransferClosedEvent
│   ├── OutboundVoucherIssuedEvent
│   └── InventoryAdjustmentRecordedEvent
└── internal/
    ├── domain/        SalesOrder, Sale, SaleReturn, StockTransfer, OutboundVoucher, InventoryAdjustment
    ├── persistence/   *Repository
    ├── application/   CreateOrderService, ConvertOrderToSaleService,
    │                   CloseSaleService, CancelSaleSaga,
    │                   ProcessReturnService,
    │                   ExecuteTransferService,
    │                   IssueOutboundVoucherService,
    │                   RecordInventoryAdjustmentService
    ├── web/           SalesOrderController, SaleController, SaleReturnController,
    │                   StockTransferController, OutboundVoucherController, AdjustmentController
    └── listener/      InvoiceAuthorizedFiscalListener (atualiza sale.invoice_id),
                       InvoiceCancelledFiscalListener (gatilho Saga),
                       ContractRecurringSaleListener (consumido de contratos)
```

## Casos de uso e endpoints

### Pedidos

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/movimentacao/sales-orders` | `pedido.consultar` |
| `POST` | `/api/v1/movimentacao/sales-orders` | `pedido.criar` |
| `GET`  | `/api/v1/movimentacao/sales-orders/{id}` | `pedido.consultar` |
| `PATCH`| `/api/v1/movimentacao/sales-orders/{id}` | `pedido.editar` |
| `POST` | `/api/v1/movimentacao/sales-orders/{id}/submit` | `pedido.criar` (envia para aprovação) |
| `POST` | `/api/v1/movimentacao/sales-orders/{id}/approve` | `pedido.aprovar` |
| `POST` | `/api/v1/movimentacao/sales-orders/{id}/reject` | `pedido.aprovar` |
| `POST` | `/api/v1/movimentacao/sales-orders/{id}/convert` | `venda.criar` (converte em venda) |

### Vendas

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/movimentacao/sales` | `venda.consultar` |
| `POST` | `/api/v1/movimentacao/sales` | `venda.criar` (`Idempotency-Key` obrigatório) |
| `GET`  | `/api/v1/movimentacao/sales/{id}` | `venda.consultar` |
| `POST` | `/api/v1/movimentacao/sales/{id}/close` | `venda.fechar` |
| `POST` | `/api/v1/movimentacao/sales/{id}/cancel` | `venda.cancelar` + step-up |

### Devoluções

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/movimentacao/returns` | `devolucao.consultar` |
| `POST` | `/api/v1/movimentacao/returns` | `devolucao.criar` (referencia `saleId`) |
| `POST` | `/api/v1/movimentacao/returns/{id}/close` | `devolucao.fechar` |

### Transferências

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/movimentacao/transfers` | `transferencia.consultar` |
| `POST` | `/api/v1/movimentacao/transfers` | `transferencia.criar` |
| `POST` | `/api/v1/movimentacao/transfers/{id}/close` | `transferencia.fechar` |

### Saída avulsa

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/movimentacao/outbound-vouchers` | `saida-avulsa.criar` + step-up |
| `GET`  | `/api/v1/movimentacao/outbound-vouchers` | `saida-avulsa.consultar` |

### Ajuste de inventário

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/movimentacao/inventory-adjustments` | `estoque.ajustar` + step-up |
| `GET`  | `/api/v1/movimentacao/inventory-adjustments` | `estoque.consultar` |

## Eventos

```java
public record SalesOrderCreatedEvent(SalesOrderId id, TenantId tenantId, BranchId branchId, PersonId customerId, Money total, Instant occurredAt) {}
public record SalesOrderApprovedEvent(SalesOrderId id, UserId by, Instant occurredAt) {}
public record SalesOrderConvertedEvent(SalesOrderId orderId, SaleId saleId, Instant occurredAt) {}

public record SaleOpenedEvent(SaleId id, TenantId tenantId, BranchId branchId, PersonId customerId, Money total, Instant occurredAt) {}
public record SaleClosedEvent(SaleId id, TenantId tenantId, BranchId branchId, PersonId customerId,
    List<SaleLine> lines, Money total, PaymentConditionRef condition, Instant occurredAt) {}
public record SaleCancellationRequestedEvent(SaleId id, TenantId tenantId, String reason, UserId by, Instant occurredAt) {}
public record SaleCancellationCompletedEvent(SaleId id, TenantId tenantId, Instant occurredAt) {}
public record SaleCancellationFailedEvent(SaleId id, TenantId tenantId, String stepFailed, String detail, Instant occurredAt) {}

public record SaleReturnClosedEvent(ReturnId id, SaleId saleId, TenantId tenantId, Money refundValue, Instant occurredAt) {}

public record StockTransferClosedEvent(TransferId id, BranchId origin, BranchId dest, Money totalCost, Instant occurredAt) {}
public record OutboundVoucherIssuedEvent(UUID id, BranchId branchId, OutboundReason reason, Instant occurredAt) {}
public record InventoryAdjustmentRecordedEvent(UUID id, BranchId branchId, int linesCount, Money totalDelta, UserId by, Instant occurredAt) {}
```

Consumidores:

- **`estoque`**: `SaleClosedEvent` → `ship()` síncrono na transação de fechamento (não via evento — port direto). `SaleCancellationRequestedEvent` → estorna estoque (Saga). Idem `SaleReturnClosedEvent`, `StockTransferClosedEvent`, `OutboundVoucherIssuedEvent`, `InventoryAdjustmentRecordedEvent`.
- **`financeiro`**: `SaleClosedEvent` → cria MVF a receber + parcelas conforme `payment_condition`. `SaleReturnClosedEvent` → estorna MVF / cria MVF a pagar (devolve dinheiro).
- **`fiscal`**: `SaleClosedEvent` opcionalmente dispara `from-sale/{id}` (config tenant `auto_issue_invoice=true`). `SaleReturnClosedEvent` → emite NFe de devolução.
- **`auditoria`**: tudo.

## Regras de negócio

### Pedido

- **RN-MOV-001**: Pedido **não** afeta estoque nem financeiro. Apenas registra intenção. Pode reservar estoque opcionalmente via flag (v1.1+).
- **RN-MOV-002**: Pedido aprovado pode ser convertido em **uma** venda. Conversão valida estoque atual e aplica preço corrente da `price_table` se não congelado.
- **RN-MOV-003**: Pedido expirado (`valid_until` < hoje) não pode ser convertido sem renovação manual.

### Venda

- **RN-MOV-004**: Venda é criada `OPEN` (rascunho mutável); só `close` baixa estoque e gera MVF. Esse passo é **idempotente** via `Idempotency-Key`.
- **RN-MOV-005**: Fechamento da venda exige saldo suficiente em estoque para cada SKU (configurável por tenant para permitir negativo).
- **RN-MOV-006**: Limite de crédito do cliente validado em vendas com `payment_condition=APRAZO`. Excede limite → `409 venda.credit-limit-exceeded`. Override exige permission `venda.credito.exceder` + audit.
- **RN-MOV-007**: Desconto por item enforced contra `packaging.discount_max` da tabela de preço. Desconto adicional exige permission `venda.desconto.exceder`.
- **RN-MOV-008**: Venda fechada é **imutável** — alterações exigem cancelamento + nova venda.
- **RN-MOV-009**: Kits são explodidos no fechamento: cada componente vira `sale_item` adicional ou virtualizado (decisão: virtual em `sale_item` para legibilidade, com `parent_line_number` referenciando o kit pai — adicionar campo em v1.1+; em v1 simplificamos com componentes inline).

### Cancelamento (Saga)

- **RN-MOV-010**: Cancelamento de venda fechada é **Saga compensatória**:
  1. Marca `sale.status='CANCELLATION_IN_PROGRESS'` (estado transiente).
  2. Publica `SaleCancellationRequestedEvent`.
  3. Listener `estoque` devolve mercadoria (REVERSAL); emite `StockReversedFromSaleEvent`.
  4. Listener `financeiro` estorna MVF (`ReceivableReversedEvent`).
  5. Listener `fiscal` cancela NFe se houver (`InvoiceCancelledEvent`).
  6. Coordenador (`CancelSaleSaga`) consome os 3 eventos de conclusão e finaliza `sale.status='CANCELLED'`.
  7. Se algum step falha terminalmente → `status='PARTIAL_CANCELLATION_FAILED'` + alerta P2 ao OWNER + Slack #ops.
- **RN-MOV-011**: Cancelamento exige `cancellation_reason` ≥ 10 caracteres + step-up.
- **RN-MOV-012**: Janela para cancelamento configurável (default 30 dias). Após, status `LOCKED_FOR_CANCELLATION` impede o fluxo.

### Devolução

- **RN-MOV-013**: Devolução referencia **venda existente fechada** (não pode devolver pedido).
- **RN-MOV-014**: Cada item de devolução tem **3 quantidades**: `quantity_returned` (volta ao estoque), `quantity_lost` (perda total, não retorna mas reduz dívida do cliente), `quantity_used` (cliente já consumiu, não devolve nem reduz dívida).
- **RN-MOV-015**: Soma das 3 ≤ `sale_item.quantity` (validação CHECK).
- **RN-MOV-016**: Refund value = `sum(quantity_returned + quantity_lost) × unit_price` (qty_used não gera refund).
- **RN-MOV-017**: Devolução fechada gera NFe de devolução automaticamente (CFOP 1xxx/2xxx).
- **RN-MOV-018**: MVF estornada proporcionalmente: se cliente pagou parcial, refund_value vira MVF a pagar (devolver dinheiro); se não pagou, parcelas pendentes são reduzidas.

### Transferência

- **RN-MOV-019**: Transferência entre filiais com **mesmo CNPJ** (matriz + filial): par OUTBOUND/INBOUND atômico, sem NFe.
- **RN-MOV-020**: Transferência entre filiais com **CNPJs distintos** (mesmo tenant, multi-empresa): gera NFe com CFOP 5152/6152.
- **RN-MOV-021**: Transferência entre locais da mesma filial: sem NFe, par atômico interno.

### Saída avulsa

- **RN-MOV-022**: Reduz estoque (`OUTBOUND` com `reason='OUTBOUND_VOUCHER'`). Não gera MVF. Pode gerar NFe (CFOP 5910 brinde, 5916 amostra, 5949 outros) — opcional, configurável.
- **RN-MOV-023**: Requer step-up e justificativa.

### Ajuste de inventário

- **RN-MOV-024**: Ajuste manual sem origem comercial. Cada linha tem `expected_qty`, `counted_qty`, `delta` calculado. Estoque é ajustado pelo `delta`.
- **RN-MOV-025**: Exige step-up + permission `estoque.ajustar` + justificativa.

## Integrações

- **Módulo `cadastros`**: cliente, vendedor, filial via QueryPorts.
- **Módulo `produto`**: SKU + preço via `PriceLookupPort`.
- **Módulo `estoque`**: `StockInventoryPort` para todas as operações (ship, reserve, transfer, voucher, adjust, reverse).
- **Módulo `financeiro`**: cria MVF via listener `SaleClosedEvent`.
- **Módulo `fiscal`**: emite NFe a partir de venda/devolução/transferência inter-CNPJ; cancelamento via Saga.
- **Módulo `auditoria`**: tudo.
- **Módulo `contratos`**: ContractInvoiceGeneratedEvent pode disparar criação de venda recorrente (v1.1+; em v1 contrato pode gerar MVF direto sem venda).

## Critérios de aceitação

- **Dado** pedido aprovado, **quando** convertido, **então** venda nova é criada `OPEN` referenciando o pedido; estoque ainda não baixado.
- **Dado** venda `OPEN`, **quando** close é chamado com saldo suficiente, **então** estoque é baixado (OUTBOUND), MVF é criada com parcelas, NFe é emitida se `auto_issue_invoice=true`, e evento `SaleClosedEvent` é publicado.
- **Dado** venda `OPEN`, **quando** close é chamado com saldo insuficiente em qualquer SKU, **então** falha com `409 stock.insufficient`, sem efeito colateral.
- **Dado** venda `OPEN`, **quando** close é chamado e cliente excede credit limit, **então** falha com `409 venda.credit-limit-exceeded`.
- **Dado** venda fechada, **quando** cancel é chamado com motivo + step-up, **então** Saga inicia, e ao final: estoque devolvido, MVF estornada, NFe cancelada, status `CANCELLED`.
- **Dado** venda fechada com NFe `AUTHORIZED`, **quando** cancel inicia Saga e SEFAZ recusa cancelamento (prazo expirou), **então** status `PARTIAL_CANCELLATION_FAILED` + alerta operacional.
- **Dado** devolução parcial (5 de 10), **quando** fechada com 5 returned + 0 lost + 0 used, **então** estoque +5, MVF reduzida proporcionalmente, NFe devolução emitida.
- **Dado** transferência inter-filial CNPJ distinto, **quando** fechada, **então** NFe gerada, OUTBOUND na origem, INBOUND no destino, todos atômicos.
- **Dado** ajuste de inventário com delta -3 em SKU X, **quando** registrado, **então** OUTBOUND/ADJUSTMENT de 3 unidades aplicado, sem MVF, com audit log.

## Não-funcionais

- **Latência POST sale (open)**: p95 < 300ms.
- **Latência POST close**: p95 < 1s (envolve estoque + MVF + opcionalmente NFe se síncrono).
- **Throughput venda**: 30 vendas/min por filial sem degradação.
- **Saga cancelamento**: completa em < 10s típico; alerta se > 30s.

## Roadmap

- **v1.1**: reserva automática de estoque em pedidos; comissão por vendedor calculada no fechamento; promoções/campanhas; integração de pedidos de e-commerce (webhook in).
- **v1.2**: split orders (1 pedido, múltiplas vendas em momentos diferentes); pedidos com data de entrega futura programada; backorder.
- **v2**: motor de regras de preço configurável; programa de fidelidade; venda assistida com sugestão.
