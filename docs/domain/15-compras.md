# 15 — Compras

Módulo `compras` — fluxo de **entrada** de mercadorias: solicitação de compra, cotação, pedido de compra, recebimento, devolução a fornecedor.

## Escopo

**Cobre**: Solicitação de Compra (SC), cotação com múltiplos fornecedores, decisão e geração de Pedido de Compra (PC), recebimento físico (INBOUND inline com custo), conferência, devolução a fornecedor, importação de XML de NFe do fornecedor (via DFe), criação de compra a partir de NFe recebida.

**Não cobre v1**: aprovação por alçada complexa (apenas aprovação flat com permission), workflow de bidding automático, planejamento MRP, integração com plataformas de marketplace.

## Conceitos centrais

- **Solicitação de Compra (`purchase_request`, SC)**: pedido interno de necessidade, originado por departamento/funcionário ou job de reposição. Status: `DRAFT`, `PENDING_APPROVAL`, `APPROVED`, `REJECTED`, `QUOTING`, `ORDERED`, `CANCELLED`.
- **Cotação (`quotation` + `quotation_item`)**: solicita preço para N fornecedores. Recebe respostas e compara item-por-item.
- **Pedido de Compra (`purchase_order`, PC)**: contrato formal com fornecedor. Gerado a partir de cotação vencedora ou direto (compra rápida). Status: `OPEN`, `SENT`, `PARTIALLY_RECEIVED`, `RECEIVED`, `CANCELLED`.
- **Recebimento (`purchase_receipt`)**: registra entrada física da mercadoria. Pode ser parcial. Cada item recebido vira INBOUND no estoque com custo + MVF a pagar.
- **Devolução a fornecedor (`supplier_return`)**: estorna recebimento (parcial ou total), gera NFe de devolução de saída.

## Modelo de dados

```sql
CREATE TABLE purchase_request (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    number          bigint NOT NULL,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    requested_by    uuid NOT NULL,                  -- user
    department_id   uuid REFERENCES catalog_department(id),
    cost_center_id  uuid REFERENCES cost_center(id),
    needed_by       date,
    priority        text CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')) DEFAULT 'NORMAL',
    status          text NOT NULL CHECK (status IN ('DRAFT','PENDING_APPROVAL','APPROVED','REJECTED','QUOTING','ORDERED','CANCELLED')),
    justification   text,
    approved_by     uuid,
    approved_at     timestamptz,
    rejected_by     uuid,
    rejected_at     timestamptz,
    rejection_reason text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number)
);

CREATE TABLE purchase_request_item (
    id              uuid PRIMARY KEY,
    request_id      uuid NOT NULL REFERENCES purchase_request(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid REFERENCES packaging(id),    -- nulo permitido para item livre
    description     text NOT NULL,
    quantity        numeric(15,4) NOT NULL,
    estimated_unit_cost numeric(15,4),
    notes           text,
    UNIQUE (request_id, line_number)
);

CREATE TABLE quotation (
    id              uuid PRIMARY KEY,
    number          bigint NOT NULL,
    request_id      uuid REFERENCES purchase_request(id),
    branch_id       uuid NOT NULL REFERENCES branch(id),
    status          text NOT NULL CHECK (status IN ('OPEN','SENT','RECEIVING','CLOSED','CANCELLED')),
    deadline        date,
    created_at      timestamptz NOT NULL DEFAULT now(),
    closed_at       timestamptz,
    UNIQUE (branch_id, number)
);

CREATE TABLE quotation_supplier (
    quotation_id    uuid NOT NULL REFERENCES quotation(id) ON DELETE CASCADE,
    supplier_id     uuid NOT NULL REFERENCES person(id),
    status          text NOT NULL CHECK (status IN ('INVITED','RESPONDED','DECLINED','EXPIRED')),
    invited_at      timestamptz NOT NULL DEFAULT now(),
    responded_at    timestamptz,
    payment_condition_id uuid REFERENCES catalog_payment_condition(id),
    delivery_days   int,
    freight_value   numeric(15,2) DEFAULT 0,
    notes           text,
    PRIMARY KEY (quotation_id, supplier_id)
);

CREATE TABLE quotation_response (
    id              uuid PRIMARY KEY,
    quotation_id    uuid NOT NULL REFERENCES quotation(id),
    supplier_id     uuid NOT NULL REFERENCES person(id),
    request_item_line int NOT NULL,                  -- referencia line_number da PR
    packaging_id    uuid REFERENCES packaging(id),
    description     text NOT NULL,
    quantity        numeric(15,4) NOT NULL,
    unit_price      numeric(15,4) NOT NULL,
    notes           text,
    UNIQUE (quotation_id, supplier_id, request_item_line)
);

CREATE TABLE purchase_order (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    number          bigint NOT NULL,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    supplier_id     uuid NOT NULL REFERENCES person(id),
    quotation_id    uuid REFERENCES quotation(id),
    request_id      uuid REFERENCES purchase_request(id),
    payment_condition_id uuid REFERENCES catalog_payment_condition(id),
    status          text NOT NULL CHECK (status IN ('OPEN','SENT','PARTIALLY_RECEIVED','RECEIVED','CANCELLED')),
    delivery_date_expected date,
    total_products  numeric(15,2) NOT NULL DEFAULT 0,
    total_freight   numeric(15,2) NOT NULL DEFAULT 0,
    total           numeric(15,2) NOT NULL DEFAULT 0,
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number)
);

CREATE TABLE purchase_order_item (
    id              uuid PRIMARY KEY,
    order_id        uuid NOT NULL REFERENCES purchase_order(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity_ordered  numeric(15,4) NOT NULL,
    quantity_received numeric(15,4) NOT NULL DEFAULT 0,
    unit_price      numeric(15,4) NOT NULL,
    discount_value  numeric(15,2) NOT NULL DEFAULT 0,
    net_value       numeric(15,2) NOT NULL,
    expected_delivery date,
    UNIQUE (order_id, line_number)
);

CREATE TABLE purchase_receipt (
    id              uuid PRIMARY KEY,
    order_id        uuid NOT NULL REFERENCES purchase_order(id),
    number          bigint NOT NULL,
    received_at     timestamptz NOT NULL DEFAULT now(),
    supplier_invoice_key text,                       -- chave NFe do fornecedor
    supplier_invoice_number text,
    dfe_document_id uuid REFERENCES dfe_document(id),  -- vínculo opcional com DFe sincronizado
    freight_value   numeric(15,2) NOT NULL DEFAULT 0,
    total           numeric(15,2) NOT NULL,
    notes           text,
    user_id         uuid NOT NULL,
    idempotency_key text,
    UNIQUE (order_id, number),
    UNIQUE (idempotency_key) WHERE idempotency_key IS NOT NULL
);

CREATE TABLE purchase_receipt_item (
    id              uuid PRIMARY KEY,
    receipt_id      uuid NOT NULL REFERENCES purchase_receipt(id) ON DELETE CASCADE,
    order_item_id   uuid NOT NULL REFERENCES purchase_order_item(id),
    quantity        numeric(15,4) NOT NULL,
    unit_cost       numeric(15,4) NOT NULL,           -- custo unitário no recebimento (pode diferir do PC)
    batch_id        uuid REFERENCES stock_batch(id),  -- se controle de lote
    location_id     uuid REFERENCES stock_location(id)
);

CREATE TABLE supplier_return (
    id              uuid PRIMARY KEY,
    receipt_id      uuid NOT NULL REFERENCES purchase_receipt(id),
    number          bigint NOT NULL,
    issued_at       timestamptz NOT NULL DEFAULT now(),
    reason          text NOT NULL,
    total           numeric(15,2) NOT NULL,
    invoice_id      uuid REFERENCES invoice(id),     -- NFe de devolução de saída
    notes           text
);

CREATE TABLE supplier_return_item (
    id              uuid PRIMARY KEY,
    supplier_return_id uuid NOT NULL REFERENCES supplier_return(id) ON DELETE CASCADE,
    receipt_item_id uuid NOT NULL REFERENCES purchase_receipt_item(id),
    quantity        numeric(15,4) NOT NULL,
    unit_cost       numeric(15,4) NOT NULL
);
```

## Estrutura Java

```
dev.dimi.erp.compras
├── package-info.java   ← allowedDependencies={"cadastros","produto","estoque","fiscal","financeiro","shared"}
├── PurchaseRequestId, PurchaseOrderId, QuotationId, ReceiptId
├── eventos:
│   ├── PurchaseRequestSubmittedEvent, ApprovedEvent, RejectedEvent
│   ├── QuotationOpenedEvent, QuotationClosedEvent
│   ├── PurchaseOrderCreatedEvent, SentEvent, CancelledEvent
│   ├── PurchaseReceivedEvent
│   ├── SupplierReturnIssuedEvent
│   └── DfeImportedToReceiptEvent
└── internal/
    ├── domain/        PurchaseRequest, Quotation, PurchaseOrder, PurchaseReceipt, SupplierReturn
    ├── persistence/   *Repository
    ├── application/   SubmitRequestService, ApproveRequestService,
    │                   QuotationService, RegisterResponseService,
    │                   GenerateOrderFromQuotationService, SendOrderService, CancelOrderService,
    │                   ReceiveOrderService (INBOUND), SupplierReturnService,
    │                   CreateReceiptFromDfeService
    ├── web/           PurchaseRequestController, QuotationController, PurchaseOrderController,
    │                   PurchaseReceiptController, SupplierReturnController
    └── listener/      DfeDocumentReceivedListener (oferece criar PC/recebimento a partir do XML)
```

## Casos de uso e endpoints

### Solicitações de compra

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/compras/requests` | `compra.consultar` |
| `POST` | `/api/v1/compras/requests` | `compra.solicitar` |
| `GET`  | `/api/v1/compras/requests/{id}` | `compra.consultar` |
| `POST` | `/api/v1/compras/requests/{id}/submit` | `compra.solicitar` |
| `POST` | `/api/v1/compras/requests/{id}/approve` | `compra.aprovar` |
| `POST` | `/api/v1/compras/requests/{id}/reject` | `compra.aprovar` |

### Cotações

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/compras/quotations` | `compra.cotar` (cria a partir de request approved ou ad-hoc) |
| `POST` | `/api/v1/compras/quotations/{id}/suppliers` | `compra.cotar` (adiciona fornecedor convidado) |
| `POST` | `/api/v1/compras/quotations/{id}/responses` | `compra.cotar` (registra resposta de fornecedor) |
| `POST` | `/api/v1/compras/quotations/{id}/close` | `compra.cotar` (encerra para decisão) |

### Pedidos de compra

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/compras/orders/from-quotation/{quotationId}` | `compra.pedir` (gera N PCs a partir de cotação fechada) |
| `POST` | `/api/v1/compras/orders` | `compra.pedir` (compra rápida sem SC/cotação) |
| `GET`  | `/api/v1/compras/orders` | `compra.consultar` |
| `GET`  | `/api/v1/compras/orders/{id}` | `compra.consultar` |
| `POST` | `/api/v1/compras/orders/{id}/send` | `compra.pedir` (marca como SENT; envia email opcional) |
| `POST` | `/api/v1/compras/orders/{id}/cancel` | `compra.cancelar` + step-up |

### Recebimento

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/compras/orders/{id}/receive` | `compra.receber` (`Idempotency-Key` obrigatório) |
| `GET`  | `/api/v1/compras/receipts/{id}` | `compra.consultar` |
| `POST` | `/api/v1/compras/receipts/from-dfe/{dfeDocumentId}` | `compra.receber` (gera PC + recebimento a partir de XML) |
| `POST` | `/api/v1/compras/supplier-returns` | `compra.devolver-fornecedor` + step-up |

## Eventos

```java
public record PurchaseRequestSubmittedEvent(PurchaseRequestId id, TenantId tenantId, BranchId branchId, UserId requestedBy, Instant occurredAt) {}
public record PurchaseRequestApprovedEvent(PurchaseRequestId id, UserId approvedBy, Instant occurredAt) {}
public record QuotationClosedEvent(QuotationId id, TenantId tenantId, Instant occurredAt) {}
public record PurchaseOrderCreatedEvent(PurchaseOrderId id, TenantId tenantId, BranchId branchId, PersonId supplierId, Money total, Instant occurredAt) {}
public record PurchaseOrderSentEvent(PurchaseOrderId id, Instant occurredAt) {}
public record PurchaseOrderCancelledEvent(PurchaseOrderId id, String reason, Instant occurredAt) {}
public record PurchaseReceivedEvent(ReceiptId receiptId, PurchaseOrderId orderId, TenantId tenantId, BranchId branchId,
    PersonId supplierId, List<ReceivedLine> lines, Money total, PaymentConditionRef condition, Instant occurredAt) {}
public record SupplierReturnIssuedEvent(UUID supplierReturnId, ReceiptId receiptId, Money total, Instant occurredAt) {}
public record DfeImportedToReceiptEvent(UUID dfeId, ReceiptId receiptId, Instant occurredAt) {}
```

Consumidores:

- **`estoque`**: chamado **síncrono** via `StockInventoryPort.receive()` na transação do recebimento (INBOUND inline). Listener no evento `SupplierReturnIssuedEvent` faz OUTBOUND.
- **`financeiro`**: listener `PurchaseReceivedEvent` cria MVF a pagar com parcelas conforme `payment_condition` do PC.
- **`fiscal`**: listener `SupplierReturnIssuedEvent` emite NFe de devolução de saída. Recebimento de mercadoria opcionalmente cria invoice de entrada se XML do fornecedor estiver disponível.
- **`auditoria`**: tudo.

## Regras de negócio

### Solicitação e aprovação

- **RN-COM-001**: SC pode ser submetida sem aprovação prévia se solicitante tem permission `compra.solicitar.aprovacao-direta` (default OWNER/MANAGER).
- **RN-COM-002**: Sem essa permission, SC vai para `PENDING_APPROVAL` e aprovador (`compra.aprovar`) decide.
- **RN-COM-003**: Aprovação por alçada simples no v1: tenant pode configurar limite de valor por role (`role_purchase_limit(role_id, max_value)`); SC acima do limite do solicitante exige aprovação superior. Implementação simples; alçada complexa fica para v1.1.
- **RN-COM-004**: SC aprovada pode ser convertida em cotação (`QUOTING`) ou direto em PC quando fornecedor já conhecido (`compra rápida`).

### Cotação

- **RN-COM-005**: Cotação convida múltiplos fornecedores; cada um responde com `quotation_response` item-por-item.
- **RN-COM-006**: Resposta pode omitir itens (fornecedor não cota tudo).
- **RN-COM-007**: Decisão (qual fornecedor escolher por item) é **manual** no v1 — UI compara e operador escolhe. v1.1+ pode automatizar (escolha pelo menor preço + prazo, com regras tenant-specific).
- **RN-COM-008**: Cotação fechada gera **N PCs** (um por fornecedor selecionado). Atomicamente.

### Pedido de compra

- **RN-COM-009**: PC sem cotação é permitido (compra rápida) com permission `compra.pedir.sem-cotacao`.
- **RN-COM-010**: PC enviado (`SENT`) pode ainda ser cancelado se nenhum item recebido. `PARTIALLY_RECEIVED` permite cancelamento dos itens não recebidos.
- **RN-COM-011**: Email ao fornecedor com PDF do PC é opcional (configurável). Endpoint `send` apenas marca status; envio é via `notificacoes` (futuro v1.1).

### Recebimento

- **RN-COM-012**: Recebimento é **fato consumado** — entra no estoque (INBOUND) com custo real do recebimento (pode diferir do PC: preços trocam, frete entra). Gera MVF a pagar via listener.
- **RN-COM-013**: Recebimento parcial é permitido. `purchase_order_item.quantity_received` acumula; status do PC vira `PARTIALLY_RECEIVED` ou `RECEIVED` quando completo.
- **RN-COM-014**: Idempotência por `Idempotency-Key` no header.
- **RN-COM-015**: Recebimento que excede `quantity_ordered` exige permission `compra.receber.excesso` + audit. Aceitar tipicamente até X% configurável (default 5%).
- **RN-COM-016**: `unit_cost` no recebimento alimenta o WAC do SKU (regra estoque). Frete é rateado proporcionalmente entre itens conforme valor (regra `FreightAllocationService`).

### Importação de DFe

- **RN-COM-017**: NFe recebida via DFe (`dfe_document`) pode ser convertida em recebimento via `from-dfe/{id}` — sistema busca/cria PC matching ou cria nova compra direta.
- **RN-COM-018**: Matching automático: por CNPJ do fornecedor + chave fiscal já vinculada a PC. Se ambíguo, requer escolha manual.
- **RN-COM-019**: Itens do XML são mapeados para SKU local via NCM/EAN ou descrição (heurística + confirmação humana).

### Devolução a fornecedor

- **RN-COM-020**: Devolução referencia recebimento. Reduz estoque (OUTBOUND), estorna MVF a pagar proporcionalmente, emite NFe de devolução (CFOP 5xxx/6xxx de saída).
- **RN-COM-021**: Step-up + permission `compra.devolver-fornecedor`.

## Integrações

- **Módulo `cadastros`**: fornecedor (Person com modality SUPPLIER), filial, departamento, centro de custo.
- **Módulo `produto`**: SKU.
- **Módulo `estoque`**: `StockInventoryPort.receive()` no recebimento.
- **Módulo `fiscal`**: `dfe_document` para importação; NFe de devolução em `supplier_return`.
- **Módulo `financeiro`**: cria MVF a pagar; `cost_center_id` propagado.
- **Módulo `auditoria`**: tudo.

## Critérios de aceitação

- **Dado** uma SC submetida por usuário sem aprovação direta, **quando** submit, **então** status `PENDING_APPROVAL` + evento `PurchaseRequestSubmittedEvent`. Aprovador recebe notificação (futuro v1.1).
- **Dado** SC aprovada com 3 itens, **quando** cotação criada e 2 fornecedores respondem, **então** cotação tem 2 respostas; ao fechar, operador escolhe vencedor por item e PCs são gerados.
- **Dado** PC com 10 unidades de SKU X a R$ 100, **quando** recebimento parcial de 6 a R$ 100, **então** estoque tem +6 (WAC recalculado), MVF a pagar de R$ 600 criada, PC `PARTIALLY_RECEIVED`. Segundo recebimento de 4 fecha PC.
- **Dado** PC `SENT` sem recebimentos, **quando** cancelar, **então** status `CANCELLED`, sem efeitos no estoque/financeiro.
- **Dado** PC `PARTIALLY_RECEIVED`, **quando** cancelar com permission, **então** apenas itens não recebidos cancelados; o que foi recebido permanece.
- **Dado** NFe de fornecedor sincronizada via DFe, **quando** `from-dfe`, **então** sistema cria PC + recebimento + estoque + MVF em transação atômica; usuário valida mapeamento de SKU.
- **Dado** recebimento, **quando** devolução parcial é registrada, **então** estoque −qtd devolvida, MVF a pagar reduzida, NFe de devolução de saída emitida.

## Não-funcionais

- **Latência POST recebimento**: p95 < 1s (envolve N INBOUNDs + MVF).
- **Throughput**: 10 recebimentos/min por filial sem degradação.
- **Saga DFe → PC + recebimento**: < 5s; reversível em caso de erro de mapeamento.

## Roadmap

- **v1.1**: alçada por valor multinível (escalonamento automático); cotação por API/email (fornecedor responde via link); email automático ao fornecedor com PDF do PC; previsão de demanda básica.
- **v1.2**: matching automático NFe→PC com ML; integração com plataformas de compra; análise de fornecedor (score interno).
- **v2**: MRP simples; planejamento por curva ABC; integração com sistemas de logística.
