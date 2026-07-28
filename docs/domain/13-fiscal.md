# 13 — Fiscal

Módulo `fiscal` — emissão e gestão de documentos fiscais brasileiros (NFe modelo 55, NFCe modelo 65, NFSe). Integração com SEFAZ/prefeituras delegada ao **ACBr** rodando como sidecar.

**Estado v1**: slice 8.1 entrega apenas a foundation (schema + entidades + repos). DfeDocument/DfeSyncState ficam para 8.5. CONTINGENCY/CCe/inutilização entram em 8.6.

## Escopo

**Cobre**: modelo de domínio agnóstico (`invoice`, `invoice_item`, `tax_calculation`), port `FiscalProvider` com adapter ACBr, configuração tributária (CFOP, CST/CSOSN, alíquotas), séries por filial e modelo, emissão a partir de venda/compra/OS/manual, cancelamento, CCe (Carta de Correção), inutilização de numeração, contingência (modo offline), DFe (NSU + manifestação), SPED Fiscal e Contribuições (export), DANFE (geração via ACBr).

**Não cobre v1**: PIS/COFINS não-cumulativo complexo (cobre cumulativo básico), SPED ECF, SPED Reinf, EFD-Reinf, MDF-e (manifesto), CT-e (transporte) — adiados para v1.1+.

## Conceitos centrais

- **Invoice (`invoice`)**: documento fiscal abstrato. Tem `model` (`NFE`, `NFCE`, `NFSE`), `series`, `number`, `status` (`DRAFT`, `PENDING`, `AUTHORIZED`, `REJECTED`, `CANCELLED`, `CONTINGENCY`), `key` (chave de 44 dígitos NFe/NFCe ou identificador NFSe), origem via `origin_type` (`SALE`, `PURCHASE`, `OS`, `MANUAL`, `CONTRACT`) + `origin_id`.
- **Invoice Item (`invoice_item`)**: linha do documento. Quantidade, valor unitário, descontos, CFOP, CST/CSOSN, NCM. Pode ter `tax_calculation` detalhado.
- **Tax Calculation (`tax_calculation`)**: detalhamento de cálculo por imposto (ICMS, IPI, PIS, COFINS, ISSQN) por item ou consolidado.
- **Série (`invoice_series`)**: numeração sequencial por filial + modelo. Próximo número via lock pessimista ou Postgres sequence dedicada por série.
- **Evento (`fiscal_event`)**: evento autorizado pela SEFAZ — `AUTHORIZATION`, `CANCELLATION`, `CCE`, `INUTILIZATION`, `REJECTION`, `MANIFESTATION`. Append-only com protocolo SEFAZ. `CANCELLATION`/`CCE`/`INUTILIZATION`/`MANIFESTATION` entram em 8.6 (8.1 expõe apenas o schema + tipos de evento; emissão só popula `AUTHORIZATION`/`REJECTION` nos slices intermediários).
- **Contingência**: modo emergencial quando SEFAZ está indisponível — tipo de emissão 5/6/7/9. Invoice fica `CONTINGENCY`; reenvio automático quando SEFAZ volta.
- **DFe (Distribuição)**: sincronização incremental com SEFAZ via NSU para baixar NFes emitidas contra a empresa (compras de terceiros).
- **FiscalProvider (port)**: interface hexagonal; implementação atual é `AcbrFiscalProvider`. Permite trocar para SaaS fiscal sem alterar domínio.

## Modelo de dados

```sql
CREATE TABLE invoice_series (
    id              uuid PRIMARY KEY,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    model           text NOT NULL CHECK (model IN ('NFE','NFCE','NFSE')),
    series          text NOT NULL,                  -- '001', '002', etc
    next_number     bigint NOT NULL DEFAULT 1,
    is_active       boolean NOT NULL DEFAULT true,
    UNIQUE (branch_id, model, series)
);

CREATE TABLE invoice (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    model           text NOT NULL CHECK (model IN ('NFE','NFCE','NFSE')),
    series          text NOT NULL,
    number          bigint NOT NULL,
    issued_at       timestamptz NOT NULL DEFAULT now(),
    status          text NOT NULL CHECK (status IN ('DRAFT','PENDING','AUTHORIZED','REJECTED','CANCELLED','CONTINGENCY','INUTILIZED')),

    operation       text NOT NULL CHECK (operation IN ('SAIDA','ENTRADA')),
    purpose         text NOT NULL CHECK (purpose IN ('NORMAL','COMPLEMENTAR','AJUSTE','DEVOLUCAO')),
    consumer_type   text CHECK (consumer_type IN ('FINAL','CONTRIBUINTE','ISENTO')),
    payment_method  text CHECK (payment_method IN ('AVISTA','APRAZO','OUTRO')),

    customer_person_id uuid REFERENCES person(id),
    customer_cpf_cnpj  text,                          -- snapshot (consumidor final pode não ter pessoa)
    customer_name      text,
    customer_address_snapshot jsonb,

    origin_type     text CHECK (origin_type IN ('SALE','PURCHASE','OS','MANUAL','CONTRACT')),
    origin_id       uuid,

    -- chave fiscal (44 dígitos NFe/NFCe; identificador único NFSe)
    fiscal_key      text UNIQUE,
    nfe_environment text NOT NULL DEFAULT 'PRODUCAO' CHECK (nfe_environment IN ('PRODUCAO','HOMOLOGACAO')),
    emission_type   smallint NOT NULL DEFAULT 1,     -- 1=normal, 5,6,7,9=contingência

    sefaz_protocol  text,
    sefaz_authorized_at timestamptz,
    sefaz_rejection_code text,
    sefaz_rejection_reason text,

    cancellation_protocol text,
    cancelled_at    timestamptz,
    cancellation_reason text,

    total_products       numeric(15,2) NOT NULL DEFAULT 0,
    total_discount       numeric(15,2) NOT NULL DEFAULT 0,
    total_freight        numeric(15,2) NOT NULL DEFAULT 0,
    total_taxes          numeric(15,2) NOT NULL DEFAULT 0,
    total                numeric(15,2) NOT NULL,

    xml_storage_path     text,                       -- Azure Blob Storage path
    danfe_storage_path   text,

    notes           text,
    idempotency_key text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    UNIQUE (branch_id, model, series, number),
    UNIQUE (origin_type, origin_id, model) WHERE origin_type IS NOT NULL,
    UNIQUE (idempotency_key) WHERE idempotency_key IS NOT NULL
);
CREATE INDEX idx_invoice_status ON invoice(status, issued_at DESC);
CREATE INDEX idx_invoice_branch_period ON invoice(branch_id, issued_at DESC);

CREATE TABLE invoice_item (
    id              uuid PRIMARY KEY,
    invoice_id      uuid NOT NULL REFERENCES invoice(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid REFERENCES packaging(id),    -- nulo em itens manuais
    item_code       text NOT NULL,                    -- snapshot do SKU
    item_description text NOT NULL,
    ncm             char(8),
    cest            char(7),
    cfop            char(4) NOT NULL,
    origin          char(1) NOT NULL,
    quantity        numeric(15,4) NOT NULL,
    unit_commercial text,
    unit_fiscal     text,
    unit_value      numeric(15,4) NOT NULL,
    gross_value     numeric(15,2) NOT NULL,
    discount        numeric(15,2) NOT NULL DEFAULT 0,
    net_value       numeric(15,2) NOT NULL,
    UNIQUE (invoice_id, line_number)
);

CREATE TABLE tax_calculation (
    id              uuid PRIMARY KEY,
    invoice_item_id uuid NOT NULL REFERENCES invoice_item(id) ON DELETE CASCADE,
    tax             text NOT NULL CHECK (tax IN ('ICMS','ICMS_ST','IPI','PIS','COFINS','ISSQN','FCP','IBPT')),
    cst_csosn       char(3),
    base            numeric(15,2),
    rate            numeric(7,4),
    value           numeric(15,2),
    base_st         numeric(15,2),
    rate_st         numeric(7,4),
    value_st        numeric(15,2),
    mva             numeric(7,4),
    benefit_code    text,
    additional      jsonb,                            -- campos específicos por imposto
    UNIQUE (invoice_item_id, tax)
);

CREATE TABLE fiscal_event (
    id              uuid PRIMARY KEY,
    invoice_id      uuid NOT NULL REFERENCES invoice(id),
    event_type      text NOT NULL CHECK (event_type IN ('AUTHORIZATION','CANCELLATION','CCE','INUTILIZATION','REJECTION','MANIFESTATION')),
    occurred_at     timestamptz NOT NULL DEFAULT now(),
    sefaz_protocol  text,
    sefaz_status    text,
    payload_xml_path text,                            -- Blob
    notes           text,
    user_id         uuid
);

CREATE TABLE dfe_sync_state (
    branch_id       uuid PRIMARY KEY REFERENCES branch(id),
    last_nsu        bigint NOT NULL DEFAULT 0,
    last_sync_at    timestamptz
);

CREATE TABLE dfe_document (
    id              uuid PRIMARY KEY,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    fiscal_key      char(44) NOT NULL UNIQUE,
    issuer_cnpj     char(14) NOT NULL,
    issuer_name     text,
    issued_at       timestamptz,
    total           numeric(15,2),
    nsu             bigint,
    schema          text,
    xml_storage_path text,
    manifestation   text CHECK (manifestation IN ('CIENCIA','CONFIRMACAO','DESCONHECIMENTO','NAO_REALIZADA')),
    manifested_at   timestamptz,
    received_at     timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_dfe_branch_received ON dfe_document(branch_id, received_at DESC);
```

XMLs e DANFEs ficam em **Azure Blob Storage** (`tenants/<tenantId>/fiscal/<branchId>/<year-month>/<fiscalKey>.xml`).

## Estrutura Java

```
dev.dimi.erp.fiscal
├── package-info.java         ← allowedDependencies={"cadastros","produto","shared"}
├── InvoiceId, FiscalKey (record com validação 44 digits)
├── FiscalProvider.java        ← port hexagonal
│   ├── authorize(InvoiceDraft) -> AuthorizationResult
│   ├── cancel(InvoiceId, reason) -> CancellationResult
│   ├── ccLetter(InvoiceId, correction) -> CceResult
│   ├── inutilize(branchId, model, series, fromNumber, toNumber, reason) -> ...
│   ├── distribute(branchId, lastNsu) -> List<DistributionDocument>
│   └── manifest(fiscalKey, type) -> ManifestationResult
├── FiscalQueryPort.java
├── eventos:
│   ├── InvoiceAuthorizedEvent.java
│   ├── InvoiceRejectedEvent.java
│   ├── InvoiceCancelledEvent.java
│   ├── InvoiceCorrectedEvent.java (CCe)
│   ├── InvoiceInutilizedEvent.java
│   ├── ContingencyEnteredEvent.java
│   ├── ContingencyResolvedEvent.java
│   └── DfeDocumentReceivedEvent.java
└── internal/
    ├── domain/        Invoice, InvoiceItem, TaxCalculation, FiscalSeries, FiscalEvent
    ├── persistence/   *Repository
    ├── application/   EmitInvoiceService, CancelInvoiceService, CceService,
    │                   ContingencyJob, DfeSyncJob, TaxCalculationService,
    │                   NumberAllocationService
    ├── adapter/       AcbrFiscalProvider (HTTP client), AcbrXmlMapper,
    │                   BlobStorageInvoiceArchive
    ├── web/           InvoiceController, FiscalSeriesController, DfeController,
    │                   FiscalReportController (SPED)
    └── listener/      SaleClosedFiscalListener (emite NFe a partir de venda quando configurado),
                       PurchaseReceivedFiscalListener,
                       OsBilledFiscalListener
```

## Casos de uso e endpoints

### Emissão

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/fiscal/invoices` | `nfe.emitir` | Emite manualmente (corpo completo do invoice). Idempotência via header. |
| `POST` | `/api/v1/fiscal/invoices/from-sale/{saleId}` | `nfe.emitir` | Emite NFe/NFCe a partir de venda. |
| `POST` | `/api/v1/fiscal/invoices/from-purchase/{purchaseId}` | `nfe.emitir` | Emite NFe de entrada a partir de compra (raro; geralmente é entrada de XML de terceiros). |
| `POST` | `/api/v1/fiscal/invoices/from-os/{osId}` | `nfe.emitir` | Emite NFSe (serviço) + NFe (produto) de uma OS, conforme composição. |

### Pós-emissão

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/fiscal/invoices` | `nfe.consultar` |
| `GET`  | `/api/v1/fiscal/invoices/{id}` | `nfe.consultar` |
| `GET`  | `/api/v1/fiscal/invoices/{id}/xml` | `nfe.consultar` |
| `GET`  | `/api/v1/fiscal/invoices/{id}/danfe` | `nfe.consultar` (retorna PDF) |
| `POST` | `/api/v1/fiscal/invoices/{id}/cancel` | `nfe.cancelar` + step-up |
| `POST` | `/api/v1/fiscal/invoices/{id}/correct` | `nfe.cce` + step-up (CCe) |
| `POST` | `/api/v1/fiscal/invoices/{id}/retry` | `nfe.emitir` (retry de PENDING/REJECTED) |
| `POST` | `/api/v1/fiscal/series/{seriesId}/inutilize` | `nfe.inutilizar` + step-up |

### Séries

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/fiscal/series?branchId=...` | `nfe.consultar` |
| `POST` | `/api/v1/fiscal/series` | `filial.fiscal.editar` + step-up |
| `PATCH`| `/api/v1/fiscal/series/{id}` | `filial.fiscal.editar` + step-up |

### DFe

| Método | Rota | Permissão |
|---|---|---|
| `POST` | `/api/v1/fiscal/dfe/sync?branchId=...` | `dfe.sincronizar` (manual; agendamento automático) |
| `GET`  | `/api/v1/fiscal/dfe/documents?branchId=...` | `dfe.consultar` |
| `GET`  | `/api/v1/fiscal/dfe/documents/{id}/xml` | `dfe.consultar` |
| `POST` | `/api/v1/fiscal/dfe/documents/{id}/manifest` | `dfe.manifestar` |

### SPED / relatórios fiscais

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/fiscal/reports/sped-fiscal?branchId=...&from=...&to=...` | `fiscal.relatorios` (gera SPED Fiscal txt) |
| `GET`  | `/api/v1/fiscal/reports/sped-contribuicoes?branchId=...&from=...&to=...` | `fiscal.relatorios` |
| `GET`  | `/api/v1/fiscal/reports/movement-by-cfop?branchId=...&from=...&to=...` | `fiscal.relatorios` |

## Eventos

```java
public record InvoiceAuthorizedEvent(InvoiceId id, TenantId tenantId, BranchId branchId,
    String model, String series, long number, FiscalKey key, Money total, Instant occurredAt) {}
public record InvoiceRejectedEvent(InvoiceId id, TenantId tenantId, String rejectionCode, String reason, Instant occurredAt) {}
public record InvoiceCancelledEvent(InvoiceId id, TenantId tenantId, String reason, Instant occurredAt) {}
public record InvoiceCorrectedEvent(InvoiceId id, TenantId tenantId, String correctionText, int sequence, Instant occurredAt) {}
public record InvoiceInutilizedEvent(BranchId branchId, String model, String series, long fromNumber, long toNumber, String reason, Instant occurredAt) {}
public record ContingencyEnteredEvent(BranchId branchId, String reason, Instant occurredAt) {}
public record ContingencyResolvedEvent(BranchId branchId, int invoicesReprocessed, Instant occurredAt) {}
public record DfeDocumentReceivedEvent(UUID dfeId, BranchId branchId, FiscalKey key, String issuerCnpj, Money total, Instant occurredAt) {}
```

Consumidores:

- **`auditoria`**: tudo (categoria `FISCAL`).
- **`movimentacao` / `compras` / `ordem-servico`**: atualizam status do documento de origem (`sale.invoice_status`, etc).
- **`financeiro`**: `InvoiceAuthorizedEvent` opcional — gera MVF se origem permite (geralmente já criada via origem).
- **`notificacoes`**: opcional — email ao cliente com XML+DANFE como anexo.

## Regras de negócio

### Emissão e numeração

- **RN-FIS-001**: Filial sem `branch_fiscal` válido + certificado ativo + ambiente configurado → `422` com `code=fiscal.branch-not-fiscally-configured`.
- **RN-FIS-002**: Próximo número da série usa **`SELECT FOR UPDATE`** em `invoice_series` ou **Postgres SEQUENCE** dedicada por série. Garante unicidade sem gaps em concorrência (com gaps tolerados em rollback).
- **RN-FIS-003**: Emissão é **atômica** — invoice + items + tax_calculation + alocação de número numa única transação. Falha de chamada ao ACBr é tratada à parte (status `PENDING`/`REJECTED`).
- **RN-FIS-004**: Simples Nacional usa **CSOSN** (3 dígitos); Lucro Presumido/Real usa **CST** (2 dígitos). Determinado pelo `branch_fiscal.tax_regime`.
- **RN-FIS-005**: `consumer_type=FINAL` força destaque de impostos como informativo (não destacado).
- **RN-FIS-006**: CFOP padrão por operação (venda dentro UF = 5102, fora UF = 6102, devolução = 1xxx/2xxx, etc) calculado por `CfopResolver` em `application/`. Override manual permitido com auditoria.
- **RN-FIS-007**: Idempotência por **`(origin_type, origin_id, model)`** — segunda tentativa de emitir mesmo origem retorna invoice existente.
- **RN-FIS-008**: Idempotência por header `Idempotency-Key` também aceita para emissão manual.

### Pós-autorização

- **RN-FIS-009**: NFe é **imutável** após autorização. Correções via CCe (apenas campos não-fiscais).
- **RN-FIS-010**: Cancelamento exige justificativa ≥ 15 caracteres + step-up. Prazo varia por UF (default 24h; configurável `branch_fiscal.cancellation_deadline_hours`).
- **RN-FIS-011**: CCe limite 20 por nota, sequenciais; não altera valores, qty, CFOP, IE/CNPJ. Texto ≥ 15 caracteres.
- **RN-FIS-012**: REJECTED → operador pode `retry` com correções; se rejection_code indica problema permanente (CNPJ destinatário inválido, certificado expirado, etc), `retry` recusa com hint.

### Contingência

- **RN-FIS-013**: SEFAZ indisponível (timeout/erro 5xx do ACBr) → invoice marcada `CONTINGENCY` com `emission_type=5` (FS-DA) ou `9` (offline NFCe). DANFE pode ser emitido localmente.
- **RN-FIS-014**: Job de retomada (`ContingencyJob`) roda a cada 5 min: tenta autorizar invoices `CONTINGENCY`. Sucesso → `AUTHORIZED`. Falha persistente → mantém em contingência.
- **RN-FIS-015**: Tenant pode forçar saída de contingência (`POST /fiscal/contingency/exit?branchId=...`) com permission `nfe.contingencia`.

### DFe

- **RN-FIS-016**: Sincronização DFe baseada em `dfe_sync_state.last_nsu`. Job periódico (configurável, default 30min) chama `FiscalProvider.distribute()`.
- **RN-FIS-017**: NFes recebidas armazenadas em `dfe_document` + XML em Blob; geram `DfeDocumentReceivedEvent` → módulo `compras` pode oferecer "criar compra a partir do XML".
- **RN-FIS-018**: Manifestação (`CIENCIA`, `CONFIRMACAO`, `DESCONHECIMENTO`, `NAO_REALIZADA`) é opt-in por configuração de filial. Prazo legal: 180 dias após emissão.

### SPED

- **RN-FIS-019**: SPED Fiscal e Contribuições geram TXT compatível com layout em vigor (atualizado pela Receita anualmente). Período fechado, contagem por filial.
- **RN-FIS-020**: SPED não inclui invoices `DRAFT`/`PENDING`/`REJECTED`/`INUTILIZED` — apenas `AUTHORIZED` e `CANCELLED`.

## Integrações

- **ACBr (sidecar HTTP)**: `AcbrFiscalProvider` chama endpoints REST do ACBr Monitor (`/NFe/CriarEnviar`, `/NFe/Cancelar`, `/NFe/EnviarCCe`, `/DFe/DistribuicaoDFePorUltNSU`, etc).
- **Módulo `cadastros`**: certificado A1 referência via `BranchQueryPort` (lê do Blob, descriptografa senha do Key Vault, passa ao ACBr na inicialização do componente).
- **Módulo `produto`**: dados fiscais do SKU (NCM, CEST, origem, unidade fiscal).
- **Módulo `movimentacao` / `compras` / `ordem-servico`**: origem dos dados de invoice.
- **Módulo `auditoria`**: todos os eventos.
- **Azure Blob Storage**: XMLs autorizados, XMLs DFe, DANFEs PDF.

## Critérios de aceitação

- **Dado** uma venda fechada com cliente PJ contribuinte, **quando** `from-sale/{id}` é chamado, **então** NFe modelo 55 é gerada com CFOP 5102 (mesma UF) + CST adequado ao regime + status `AUTHORIZED` + chave fiscal de 44 dígitos + protocolo SEFAZ.
- **Dado** uma NFe `AUTHORIZED`, **quando** chamada `/cancel` com justificativa válida + step-up, **então** status vira `CANCELLED`, `cancellation_protocol` é populado, e evento `InvoiceCancelledEvent` é publicado.
- **Dado** uma NFe `AUTHORIZED` com erro de descrição de produto, **quando** CCe é emitida com texto de correção, **então** evento `InvoiceCorrectedEvent` é publicado, sequência 1; CCe seguinte vai para sequência 2 etc.
- **Dado** ACBr retorna timeout, **quando** emissão é tentada, **então** invoice fica em `CONTINGENCY` + evento `ContingencyEnteredEvent`; DANFE pode ser obtido; `ContingencyJob` retomará depois.
- **Dado** uma venda já com invoice `AUTHORIZED`, **quando** `from-sale/{id}` é chamado novamente, **então** retorna invoice existente (idempotência por origem).
- **Dado** uma série esgotada (`next_number` chega ao limite legal), **quando** emissão tenta usar a série, **então** retorna `422` com `code=fiscal.series-exhausted`.
- **Dado** uma sincronização DFe, **quando** retornam 3 novas NFes contra o CNPJ da filial, **então** 3 `dfe_document` são criados, XMLs salvos em Blob, e 3 `DfeDocumentReceivedEvent` publicados.
- **Dado** SPED Fiscal de Maio/2026, **quando** export, **então** TXT gerado inclui todas invoices `AUTHORIZED` + `CANCELLED` da filial no período, no layout vigente.

## Não-funcionais

- **Latência emissão p95**: < 3s (excluindo SEFAZ; cap 6s total).
- **Latência cancelamento p95**: < 2s.
- **Disponibilidade emissão**: 99% mensal (depende de SEFAZ; contingência reduz impacto de outage).
- **Throughput**: 60 emissões/min por filial sem queue (proteção rate limit configurada em [02-seguranca.md](../architecture/02-seguranca.md)).
- **Armazenamento**: ~30KB por XML autorizado; ~100KB DANFE PDF. 100k notas/mês → ~13GB/mês — Blob Storage cool tier após 90d.
- **Retenção legal**: XMLs por 5 anos no Blob; após, archive tier por mais 5 anos (compliance fiscal BR).

## Roadmap

- **v1.1**: MDF-e (manifesto de transporte), provedor SaaS alternativo (Focus NFe, Nuvem Fiscal) selecionável por configuração; auto-issue por evento (`SaleClosed → InvoiceAuthorized` automático).
- **v1.2**: CT-e (transporte); SPED ECF; SPED EFD-Reinf; tributação não-cumulativa completa; benefícios fiscais por UF; carta de correção em lote.
- **v2**: NFCe com TEF; integração direta com a Receita (sem ACBr) onde permitido; emissão de NFe consumidor com QR code padrão NFCe; pré-cálculo tributário com IBPT.

## Anti-padrões de implementação

- **Construir XML manualmente** — ACBr fornece XML completo, evita drift de schema entre versões.
- **Persistir certificado em banco em claro** — uso Blob + senha cifrada via Key Vault.
- **Cancelamento que falha externamente mas marca como cancelado localmente** — Saga: status só vira `CANCELLED` quando protocolo SEFAZ chega.
- **Hardcoding de Sicredi/banco** em rotinas de boleto fiscal — fica no módulo financeiro com adapter próprio.
- **Lógica fiscal em PL/pgSQL** — código Java; SQL só para queries de relatório.
