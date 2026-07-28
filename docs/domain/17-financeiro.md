# 17 — Financeiro

Módulo `financeiro` — núcleo financeiro do ERP: Movimentação Financeira (MVF), parcelas, pagamentos, plano de contas, centro de custo, conciliação bancária, CNAB, fluxo de caixa, congelamento de período.

## Escopo

**Cobre**: plano de contas hierárquico, centro de custo hierárquico, MVF (a pagar/receber) com parcelas e rateios, registro de pagamentos com formas múltiplas, estorno/renegociação, juros/multa, conta bancária, conciliação bancária (manual + assistida), CNAB 240/400 (geração de remessa + processamento de retorno), boleto, congelamento de período, relatórios essenciais (DRE competência, fluxo de caixa, aging).

**Não cobre v1**: contabilidade fiscal completa (SPED ECD/ECF — ficam para v2); orçado vs realizado por centro de custo (v1.1); split de pagamento Pix programático com integração bancária (v1.1+); reconciliação automática via OFX/extrato API.

## Conceitos centrais

- **Plano de contas (`chart_account`)**: classificação gerencial hierárquica (Receita, Despesa, Ativo, Passivo, Resultado). Code estrutural (`1.01.001`).
- **Centro de custo (`cost_center`)**: classificação operacional hierárquica (Filial X / Departamento Y / Projeto Z). Code estrutural.
- **Movimentação Financeira (`financial_record`)**: título a pagar (DEBIT) ou receber (CREDIT). Tem sujeito (cliente/fornecedor), origem (sale, purchase, os, contract, manual), valor total, data emissão.
- **Parcela (`financial_installment`)**: 1..N por MVF. Cada uma com `due_date`, `value`, `status` (`OPEN`, `PARTIALLY_PAID`, `PAID`, `CANCELLED`, `RENEGOTIATED`).
- **Rateio (`financial_apportionment`)**: distribuição da MVF entre contas e centros de custo. Soma deve bater com valor da MVF.
- **Pagamento (`financial_payment`)**: registro de quitação. Pode ser batch (múltiplas parcelas num mesmo lote), com forma de pagamento, conta bancária, taxas/juros/multa/desconto.
- **Conta bancária (`bank_account`)**: caixa, banco, cartão, gateway. Movimentações de pagamento alimentam saldo da conta.
- **Conciliação (`bank_reconciliation`)**: comparação entre extrato (linhas) e movimentações registradas; marca linhas como reconciliadas.
- **Período congelado (`frozen_period`)**: trava DML em MVF/pagamentos em um período fechado contabilmente.

## Modelo de dados

```sql
CREATE TABLE chart_account (
    id              uuid PRIMARY KEY,
    code            text NOT NULL UNIQUE,            -- '3.01.001'
    name            text NOT NULL,
    type            text NOT NULL CHECK (type IN ('ASSET','LIABILITY','EQUITY','REVENUE','EXPENSE','RESULT')),
    parent_id       uuid REFERENCES chart_account(id),
    is_synthetic    boolean NOT NULL DEFAULT false,   -- só agrupa, não recebe lançamento
    is_active       boolean NOT NULL DEFAULT true,
    sort_order      int NOT NULL DEFAULT 0,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE cost_center (
    id              uuid PRIMARY KEY,
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    parent_id       uuid REFERENCES cost_center(id),
    is_active       boolean NOT NULL DEFAULT true,
    sort_order      int NOT NULL DEFAULT 0
);

CREATE TABLE bank_account (
    id              uuid PRIMARY KEY,
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    type            text NOT NULL CHECK (type IN ('CASH','BANK','CARD','GATEWAY','VOUCHER')),
    bank_code       text,                            -- FEBRABAN, ex: '341'
    bank_agency     text,
    bank_account_no text,
    chart_account_id uuid REFERENCES chart_account(id),
    initial_balance numeric(15,2) NOT NULL DEFAULT 0,
    is_active       boolean NOT NULL DEFAULT true,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE financial_record (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    number          bigint NOT NULL,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    direction       text NOT NULL CHECK (direction IN ('CREDIT','DEBIT')),  -- a receber / a pagar
    counterparty_id uuid REFERENCES person(id),
    document_number text,                            -- nota fiscal, recibo, etc
    issued_at       date NOT NULL,
    total_value     numeric(15,2) NOT NULL,
    status          text NOT NULL CHECK (status IN ('OPEN','PARTIALLY_PAID','PAID','CANCELLED','RENEGOTIATED')),
    origin_type     text CHECK (origin_type IN ('sale','purchase','os','contract','manual')),
    origin_id       uuid,
    notes           text,
    idempotency_key text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number),
    UNIQUE (origin_type, origin_id) WHERE origin_type IS NOT NULL AND origin_type <> 'manual',
    UNIQUE (idempotency_key) WHERE idempotency_key IS NOT NULL
);

CREATE TABLE financial_installment (
    id              uuid PRIMARY KEY,
    record_id       uuid NOT NULL REFERENCES financial_record(id) ON DELETE CASCADE,
    sequence        int NOT NULL,
    due_date        date NOT NULL,
    original_value  numeric(15,2) NOT NULL,
    paid_value      numeric(15,2) NOT NULL DEFAULT 0,
    fine_value      numeric(15,2) NOT NULL DEFAULT 0,        -- multa
    interest_value  numeric(15,2) NOT NULL DEFAULT 0,        -- juros
    discount_value  numeric(15,2) NOT NULL DEFAULT 0,
    status          text NOT NULL CHECK (status IN ('OPEN','PARTIALLY_PAID','PAID','CANCELLED','RENEGOTIATED')),
    bank_account_id uuid REFERENCES bank_account(id),
    barcode         text,                                     -- boleto
    UNIQUE (record_id, sequence)
);
CREATE INDEX idx_installment_due ON financial_installment(due_date)
    WHERE status IN ('OPEN','PARTIALLY_PAID');

CREATE TABLE financial_apportionment (
    id                uuid PRIMARY KEY,
    record_id         uuid NOT NULL REFERENCES financial_record(id) ON DELETE CASCADE,
    chart_account_id  uuid NOT NULL REFERENCES chart_account(id),
    cost_center_id    uuid REFERENCES cost_center(id),
    value             numeric(15,2) NOT NULL,
    percentage        numeric(7,4) NOT NULL,
    notes             text
);

CREATE TABLE financial_payment (
    id              uuid PRIMARY KEY,
    batch_id        uuid NOT NULL,                            -- agrupa pagamentos do mesmo lote
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    bank_account_id uuid NOT NULL REFERENCES bank_account(id),
    method          text NOT NULL CHECK (method IN ('CASH','PIX','BOLETO','TRANSFER','CARD_DEBIT','CARD_CREDIT','CHECK','OTHER')),
    user_id         uuid NOT NULL,
    notes           text,
    idempotency_key text,
    UNIQUE (idempotency_key) WHERE idempotency_key IS NOT NULL
);

CREATE TABLE financial_payment_application (
    id              uuid PRIMARY KEY,
    payment_id      uuid NOT NULL REFERENCES financial_payment(id) ON DELETE CASCADE,
    installment_id  uuid NOT NULL REFERENCES financial_installment(id),
    value           numeric(15,2) NOT NULL,                   -- quanto aplicado nesta parcela
    fine_applied    numeric(15,2) NOT NULL DEFAULT 0,
    interest_applied numeric(15,2) NOT NULL DEFAULT 0,
    discount_applied numeric(15,2) NOT NULL DEFAULT 0
);

CREATE TABLE financial_reversal (
    id                  uuid PRIMARY KEY,
    original_payment_id uuid NOT NULL REFERENCES financial_payment(id),
    reversal_payment_id uuid NOT NULL REFERENCES financial_payment(id),     -- pagamento espelho
    reason              text NOT NULL,
    occurred_at         timestamptz NOT NULL DEFAULT now(),
    user_id             uuid NOT NULL
);

CREATE TABLE frozen_period (
    id              uuid PRIMARY KEY,
    branch_id       uuid REFERENCES branch(id),               -- nulo = todos
    period_start    date NOT NULL,
    period_end      date NOT NULL,
    frozen_at       timestamptz NOT NULL DEFAULT now(),
    frozen_by       uuid NOT NULL,
    reason          text NOT NULL,
    UNIQUE (branch_id, period_start, period_end)
);

CREATE TABLE bank_statement_line (
    id              uuid PRIMARY KEY,
    bank_account_id uuid NOT NULL REFERENCES bank_account(id),
    occurred_on     date NOT NULL,
    description     text NOT NULL,
    document_ref    text,
    debit_value     numeric(15,2),
    credit_value    numeric(15,2),
    imported_at     timestamptz NOT NULL DEFAULT now(),
    matched_payment_id uuid REFERENCES financial_payment(id),
    matched_at      timestamptz,
    UNIQUE (bank_account_id, occurred_on, description, document_ref)
);

CREATE TABLE cnab_batch (
    id              uuid PRIMARY KEY,
    bank_account_id uuid NOT NULL REFERENCES bank_account(id),
    direction       text NOT NULL CHECK (direction IN ('REMITTANCE','RETURN')),
    layout          text NOT NULL CHECK (layout IN ('CNAB240','CNAB400')),
    file_storage_path text NOT NULL,                          -- Azure Blob
    sent_at         timestamptz,
    received_at     timestamptz,
    status          text NOT NULL CHECK (status IN ('PENDING','SENT','PROCESSED','FAILED')),
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE cnab_batch_record (
    id              uuid PRIMARY KEY,
    batch_id        uuid NOT NULL REFERENCES cnab_batch(id) ON DELETE CASCADE,
    installment_id  uuid REFERENCES financial_installment(id),
    bank_status     text,                                       -- código retorno banco
    bank_message    text,
    occurred_on     date,
    value           numeric(15,2)
);
```

## Estrutura Java

```
dev.dimi.erp.financeiro
├── package-info.java   ← allowedDependencies={"cadastros","shared"}
├── FinancialRecordId, InstallmentId, PaymentId, BatchId, BankAccountId, ChartAccountId, CostCenterId
├── eventos:
│   ├── FinancialRecordCreatedEvent, CancelledEvent, RenegotiatedEvent
│   ├── InstallmentDueDateChangedEvent
│   ├── PaymentProcessedEvent, ReversedEvent
│   ├── ReceivableReversedEvent (consumido por movimentação para Sagas)
│   ├── PeriodFrozenEvent, PeriodUnfrozenEvent
│   ├── CnabRemittanceGeneratedEvent, ReturnProcessedEvent
│   └── BankReconciliationMatchedEvent
└── internal/
    ├── domain/        FinancialRecord, Installment, Apportionment, Payment, BankAccount,
    │                   ChartAccount, CostCenter, FrozenPeriod, CnabBatch
    ├── persistence/   *Repository
    ├── application/   CreateRecordService, CancelRecordService, RenegotiateRecordService,
    │                   ChangeInstallmentDueDateService, CalculateFineInterestService,
    │                   ProcessPaymentBatchService, ReversePaymentService,
    │                   FreezePeriodService, UnfreezePeriodService,
    │                   GenerateCnabRemittanceService, ProcessCnabReturnService,
    │                   ReconcileBankStatementService,
    │                   ReportingService (DRE, cash flow, aging)
    ├── adapter/       CnabBancoBrasilAdapter, CnabBradescoAdapter, CnabSicrediAdapter, ...
    ├── web/           FinancialRecordController, PaymentController, BankAccountController,
    │                   ChartAccountController, CostCenterController, FrozenPeriodController,
    │                   CnabController, ReconciliationController, ReportController
    └── listener/      SaleClosedFinancialListener (cria MVF a receber),
                       PurchaseReceivedFinancialListener (cria MVF a pagar),
                       OsBilledFinancialListener,
                       ContractInvoiceGeneratedFinancialListener,
                       SaleCancellationFinancialListener (estorna MVF)
```

## Casos de uso e endpoints

### Plano de contas / Centro de custo

| Método | Rota | Permissão |
|---|---|---|
| `GET`/`POST`/`PATCH`/`DELETE` | `/api/v1/financeiro/chart-accounts` | `plano-contas.editar` |
| `GET`/`POST`/`PATCH`/`DELETE` | `/api/v1/financeiro/cost-centers` | `centro-custo.editar` |

### Conta bancária

| Método | Rota | Permissão |
|---|---|---|
| `GET`/`POST`/`PATCH` | `/api/v1/financeiro/bank-accounts` | `conta-bancaria.editar` |

### MVF e parcelas

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/financeiro/records` | `mvf.consultar` |
| `POST` | `/api/v1/financeiro/records` | `mvf.criar` |
| `GET`  | `/api/v1/financeiro/records/{id}` | `mvf.consultar` |
| `POST` | `/api/v1/financeiro/records/{id}/cancel` | `mvf.cancelar` + step-up |
| `POST` | `/api/v1/financeiro/records/{id}/renegotiate` | `mvf.renegociar` + step-up |
| `PATCH`| `/api/v1/financeiro/installments/{id}/due-date` | `mvf.editar` |
| `GET`  | `/api/v1/financeiro/installments/{id}/calculate-fine-interest?on=YYYY-MM-DD` | `mvf.consultar` |

### Pagamentos

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/financeiro/payments/batch` | `pagamento.registrar` (`Idempotency-Key` obrigatório) |
| `GET`  | `/api/v1/financeiro/payments` | `pagamento.consultar` |
| `GET`  | `/api/v1/financeiro/payments/{id}` | `pagamento.consultar` |
| `POST` | `/api/v1/financeiro/payments/{id}/reverse` | `pagamento.estornar` + step-up |

### Período congelado

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/financeiro/frozen-periods` | `periodo.consultar` |
| `POST` | `/api/v1/financeiro/frozen-periods` | `periodo.congelar` + step-up |
| `DELETE` | `/api/v1/financeiro/frozen-periods/{id}` | `periodo.descongelar` + step-up |

### CNAB

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/financeiro/cnab/remittance` | `cnab.gerar` (cria lote a partir de installments selecionadas) |
| `GET`  | `/api/v1/financeiro/cnab/remittance/{id}/file` | `cnab.gerar` (download .REM) |
| `POST` | `/api/v1/financeiro/cnab/return/upload` | `cnab.processar` (upload .RET, processa) |
| `GET`  | `/api/v1/financeiro/cnab/batches` | `cnab.consultar` |

### Conciliação bancária

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/financeiro/reconciliation/upload-statement` | `conciliacao.executar` (CSV/OFX) |
| `GET`  | `/api/v1/financeiro/reconciliation/lines?bankAccountId=...` | `conciliacao.consultar` |
| `POST` | `/api/v1/financeiro/reconciliation/lines/{id}/match` | `conciliacao.executar` |

### Relatórios

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/financeiro/reports/dre?from=...&to=...&basis=COMPETENCIA\|CAIXA` | `relatorio.financeiro` |
| `GET`  | `/api/v1/financeiro/reports/cash-flow-projection?months=N` | `relatorio.financeiro` |
| `GET`  | `/api/v1/financeiro/reports/receivables-aging?reference=...` | `relatorio.financeiro` |
| `GET`  | `/api/v1/financeiro/reports/payables-aging?reference=...` | `relatorio.financeiro` |
| `GET`  | `/api/v1/financeiro/reports/bank-balance` | `relatorio.financeiro` |

## Eventos

```java
public record FinancialRecordCreatedEvent(FinancialRecordId id, TenantId tenantId, BranchId branchId,
    Direction direction, PersonId counterpartyId, Money totalValue, String originType, UUID originId, Instant occurredAt) {}
public record FinancialRecordCancelledEvent(FinancialRecordId id, String reason, Instant occurredAt) {}
public record FinancialRecordRenegotiatedEvent(FinancialRecordId originalId, FinancialRecordId newId, Money newTotal, Instant occurredAt) {}
public record InstallmentDueDateChangedEvent(InstallmentId id, LocalDate from, LocalDate to, UserId by, Instant occurredAt) {}
public record PaymentProcessedEvent(BatchId batchId, List<InstallmentId> installments, Money totalApplied, Instant occurredAt) {}
public record PaymentReversedEvent(PaymentId originalId, PaymentId reversalId, Instant occurredAt) {}
public record ReceivableReversedEvent(FinancialRecordId id, TenantId tenantId, Money reversedAmount, String reason, Instant occurredAt) {}  // para Saga
public record PeriodFrozenEvent(UUID id, BranchId branchId, LocalDate start, LocalDate end, UserId by, Instant occurredAt) {}
public record CnabRemittanceGeneratedEvent(UUID batchId, BankAccountId account, int linesCount, Instant occurredAt) {}
public record CnabReturnProcessedEvent(UUID batchId, int matchedCount, Instant occurredAt) {}
public record BankReconciliationMatchedEvent(UUID lineId, PaymentId paymentId, Instant occurredAt) {}
```

Consumidores:

- **`movimentacao`**: `ReceivableReversedEvent` durante Saga de cancelamento de venda.
- **`compras`**: análogo na Saga de cancelamento.
- **`auditoria`**: tudo.

## Regras de negócio

### Plano de contas e centro de custo

- **RN-FIN-001**: Plano de contas hierárquico via `parent_id`. Conta sintética (`is_synthetic=true`) só agrega — não pode receber lançamento direto.
- **RN-FIN-002**: Conta com lançamentos vinculados não pode ser deletada — apenas inativada.
- **RN-FIN-003**: Centro de custo idem (hierarquia + soft delete).

### MVF e parcelas

- **RN-FIN-004**: MVF criada com 1..N parcelas conforme `payment_condition` (ou explícito). Soma de `original_value` das parcelas = `total_value` da MVF.
- **RN-FIN-005**: Rateio obrigatório (pelo menos 1 linha em `financial_apportionment`). Default: 100% para conta default configurada por tenant (`tenant_setting.default_revenue_account_id` / `default_expense_account_id`). Tenant pode customizar rateio na criação.
- **RN-FIN-006**: Parcela `OPEN` permite alteração de `due_date` (com permission `mvf.editar`). Audit log obrigatório.
- **RN-FIN-007**: Juros e multa: calculados sob demanda via `CalculateFineInterestService`. Default: multa 2% + juros 1%/mês prorata. Configurável por MVF (override).
- **RN-FIN-008**: Cancelar MVF: só permitido se nenhuma parcela tem pagamento aplicado. Status `CANCELLED`, parcelas todas `CANCELLED`.
- **RN-FIN-009**: Renegociar MVF: cria nova MVF que substitui parte ou todo o saldo aberto da MVF original. Original vira `RENEGOTIATED`, nova MVF tem `notes` referenciando original. Parcelas afetadas viram `RENEGOTIATED`.
- **RN-FIN-010**: Idempotência por `(origin_type, origin_id)` quando origem não é manual — evita criar 2 MVFs para mesma venda/compra/OS/contrato.

### Pagamentos

- **RN-FIN-011**: Pagamento é **sempre** batch (1..N aplicações em parcelas). Endpoint único `/payments/batch`.
- **RN-FIN-012**: Aplicação distribui valor entre parcelas selecionadas. Pode liquidar parcial (`PARTIALLY_PAID`) ou total (`PAID`).
- **RN-FIN-013**: Juros/multa/desconto aplicados no momento do pagamento. Audit log registra valores.
- **RN-FIN-014**: Idempotency-Key obrigatório.
- **RN-FIN-015**: Estorno de pagamento cria **pagamento espelho** (valores negativos) + linha em `financial_reversal`. Parcelas afetadas voltam ao status apropriado (`OPEN` ou `PARTIALLY_PAID`).
- **RN-FIN-016**: Pagamento que atravessa período congelado é bloqueado (`409 financial.period-frozen`). Só permitido pagamento de parcela em período não-congelado.

### Período congelado

- **RN-FIN-017**: Congelamento por filial ou geral. Após congelamento, nenhuma criação/edição/exclusão de MVF ou pagamento com `occurred_at` dentro do período é permitida.
- **RN-FIN-018**: Descongelamento requer step-up + permission `periodo.descongelar` (ação ainda mais sensível que congelar).
- **RN-FIN-019**: Ambas operações têm audit log obrigatório, com motivo.

### CNAB

- **RN-FIN-020**: Geração de remessa: seleciona parcelas a pagar (DEBIT) ou cobrar (CREDIT via boleto), gera arquivo `.REM` no layout CNAB 240 ou 400 conforme banco. Armazenado em Blob.
- **RN-FIN-021**: Processamento de retorno: lê arquivo `.RET`, busca parcelas por nosso número ou referência, atualiza status conforme código bancário (liquidado, devolvido, etc), emite eventos.
- **RN-FIN-022**: Adapter por banco (`CnabBancoBrasilAdapter`, `CnabBradescoAdapter`, etc) — interface `CnabAdapter` em port hexagonal.

### Conciliação bancária

- **RN-FIN-023**: Upload de extrato (CSV ou OFX) cria `bank_statement_line` para cada linha.
- **RN-FIN-024**: Matching automático: linha conciliada com pagamento se: mesma conta, valor exato, data ±3 dias, descrição com tokens em comum. Usuário confirma matches sugeridos.
- **RN-FIN-025**: Matching manual: usuário escolhe linha + pagamento; ambos marcados como reconciliados.

### Relatórios

- **RN-FIN-026**: **DRE** (Demonstração de Resultados) — competência: agrega `financial_record` pela data de emissão; caixa: agrega `financial_payment` pela data. Estrutura via `chart_account.type` (REVENUE, EXPENSE, etc).
- **RN-FIN-027**: **Fluxo de caixa projetado** — soma de `installment.due_date` futuras + saldo atual de bank_accounts; agrupa por mês.
- **RN-FIN-028**: **Aging recebíveis/pagáveis** — buckets de atraso: `EM_DIA`, `1-7d`, `8-15d`, `16-30d`, `31-60d`, `>60d`. Filtros: cliente/fornecedor, conta, centro de custo.

## Integrações

- **Módulo `cadastros`**: counterparty (Person), branch.
- **Módulo `movimentacao`**: listener `SaleClosedEvent` cria MVF CREDIT; cancelamento publica `ReceivableReversedEvent`.
- **Módulo `compras`**: listener `PurchaseReceivedEvent` cria MVF DEBIT.
- **Módulo `ordem-servico`**: listener `OsBilledEvent` cria MVF CREDIT.
- **Módulo `contratos`**: gera MVF recorrente via listener.
- **Módulo `fiscal`**: relacionamento opcional (relatório DRE pode cruzar com NFes).
- **Módulo `auditoria`**: tudo.
- **Azure Blob Storage**: arquivos CNAB e extratos importados.

## Critérios de aceitação

- **Dado** uma venda fechada de R$ 1.000 com payment_condition "2x 30/60", **quando** listener `SaleClosedEvent` processa, **então** MVF CREDIT criada + 2 parcelas (R$ 500 venc. +30d, R$ 500 venc. +60d) + rateio padrão.
- **Dado** uma parcela vencida há 10 dias, **quando** `calculate-fine-interest`, **então** retorna juros = `500 × 0.01 × 10/30 = 1.67` + multa = `500 × 0.02 = 10.00`.
- **Dado** um pagamento batch quitando 2 parcelas (R$ 510 cada com juros+multa), **quando** processado com `Idempotency-Key`, **então** parcelas viram `PAID`, pagamento e aplicações criadas, evento `PaymentProcessedEvent`.
- **Dado** o mesmo `Idempotency-Key` reutilizado, **quando** retry, **então** retorna pagamento original sem efeito duplicado.
- **Dado** um pagamento `PAID`, **quando** reverse com motivo + step-up, **então** pagamento espelho criado, parcelas voltam ao status anterior, evento `PaymentReversedEvent`.
- **Dado** uma MVF original de R$ 1.000 (parcela R$ 600 aberta + R$ 400 paga), **quando** renegociar saldo aberto em 3 parcelas, **então** original vira `RENEGOTIATED`, nova MVF tem 3 parcelas somando R$ 600, evento.
- **Dado** período `2026-01-01..2026-01-31` congelado, **quando** tentativa de pagamento em 2026-01-15, **então** `409 financial.period-frozen`.
- **Dado** uma remessa CNAB de 50 parcelas a cobrar, **quando** gerada, **então** arquivo `.REM` no layout correto do banco é gerado, parcelas marcadas como "enviadas ao banco".
- **Dado** um retorno CNAB com 30 liquidações, **quando** processado, **então** 30 pagamentos criados, parcelas atualizadas.
- **Dado** DRE de competência de Maio/2026, **quando** consulta, **então** retorna agregação por conta, totalizando receitas - despesas = resultado.

## Não-funcionais

- **Latência POST payment batch** (10 parcelas): p95 < 300ms.
- **Latência DRE**: < 3s para período de 1 ano com até 50k movements (com índices apropriados).
- **Throughput**: 100 pagamentos/min sem degradação.
- **Volume**: 1M installments + 500k payments em 5 anos sem perda de performance — particionar `financial_payment_application` quando atingir 5M linhas.
- **CNAB processing**: 10k linhas em < 30s.

## Roadmap

- **v1.1**: orçado vs realizado por centro de custo; CNAB com mais bancos (Sicredi, Itaú, Santander, Caixa); reconciliação automática via OFX; Pix programático com gateway.
- **v1.2**: split de pagamento; cobrança recorrente (assinatura) integrada com gateway; webhooks de pagamento.
- **v2**: contabilidade fiscal completa (SPED ECD/ECF); previsão de fluxo de caixa com ML; multi-moeda.
