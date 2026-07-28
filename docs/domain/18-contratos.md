# 18 — Contratos

Módulo `contratos` — contratos comerciais com recorrência (locação, mensalidades, serviços continuados, manutenção), aditivos, vencimentos, geração automática de MVF.

## Escopo

**Cobre**: contrato com vigência, recorrência mensal/quinzenal/anual, itens (produto locado, serviço recorrente), reajustes contratuais (anual, IGP-M/IPCA/manual), aditivos (extensão de prazo, valor, escopo), encerramento (no termo / antecipado / inadimplência), geração de MVF a receber por competência, integração com OS de manutenção (manutenção mensal vira OS automática), notificações de vencimento.

**Não cobre v1**: workflow de aprovação de aditivos com múltiplos signatários; assinatura eletrônica (DocuSign/Clicksign integration — v1.1+); índices de reajuste obtidos automaticamente (manual no v1; integração com IBGE/FGV em v1.1); contratos com fornecedor para compras recorrentes (cabe pensar em v1.2 se houver demanda).

## Conceitos centrais

- **Contrato (`contract`)**: documento principal. Cliente, filial, vigência, condição de pagamento, valor recorrente, status.
- **Item de contrato (`contract_item`)**: linha do contrato. Pode ser **produto** (locação — reserva estoque ou não, configurável) ou **serviço** (recorrente). Tem valor, quantidade, periodicidade.
- **Ciclo de faturamento (`contract_billing_cycle`)**: registro de cada "rodada" de faturamento (Janeiro, Fevereiro, etc). Gerada por job mensal/quinzenal.
- **Aditivo (`contract_amendment`)**: registro de alteração contratual. Status: `DRAFT`, `EFFECTIVE`, `CANCELLED`. Aplica mudanças (prazo, valor, item adicionado/removido) com data de início.
- **Reajuste (`contract_adjustment`)**: aumento percentual no valor por índice (IGP-M, IPCA, ou manual). Histórico mantido.

## Modelo de dados

```sql
CREATE TABLE contract (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    number          bigint NOT NULL,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    customer_id     uuid NOT NULL REFERENCES person(id),
    contact_id      uuid REFERENCES person_contact(id),
    title           text NOT NULL,
    description     text,

    status          text NOT NULL CHECK (status IN ('DRAFT','ACTIVE','SUSPENDED','EXPIRED','CANCELLED','TERMINATED_EARLY')),
    start_date      date NOT NULL,
    end_date        date,                                -- nulo = aberto (sem prazo)
    auto_renew      boolean NOT NULL DEFAULT false,
    renewal_period_months int,                            -- se auto_renew

    recurrence      text NOT NULL CHECK (recurrence IN ('MONTHLY','BIWEEKLY','QUARTERLY','SEMIANNUAL','ANNUAL')),
    billing_day     int NOT NULL CHECK (billing_day BETWEEN 1 AND 31),  -- dia do mês
    payment_condition_id uuid REFERENCES catalog_payment_condition(id),

    base_value      numeric(15,2) NOT NULL,               -- valor base recorrente
    last_adjustment_date date,
    next_adjustment_date date,

    cost_center_id  uuid REFERENCES cost_center(id),

    cancellation_clause text,                              -- texto livre (multa, aviso prévio)
    cancellation_fee_value numeric(15,2),
    cancellation_notice_days int,

    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    activated_at    timestamptz,
    suspended_at    timestamptz,
    terminated_at   timestamptz,
    termination_reason text,
    UNIQUE (branch_id, number)
);
CREATE INDEX idx_contract_active ON contract(status, next_billing_due) WHERE status = 'ACTIVE';

CREATE TABLE contract_item (
    id              uuid PRIMARY KEY,
    contract_id     uuid NOT NULL REFERENCES contract(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    item_type       text NOT NULL CHECK (item_type IN ('PRODUCT','SERVICE')),
    packaging_id    uuid REFERENCES packaging(id),       -- quando PRODUCT
    service_code    text,                                  -- LC 116 quando SERVICE
    description     text NOT NULL,
    quantity        numeric(15,4) NOT NULL,
    unit_price      numeric(15,4) NOT NULL,
    discount_value  numeric(15,2) NOT NULL DEFAULT 0,
    net_value       numeric(15,2) NOT NULL,
    UNIQUE (contract_id, line_number)
);

CREATE TABLE contract_billing_cycle (
    id              uuid PRIMARY KEY,
    contract_id     uuid NOT NULL REFERENCES contract(id),
    competence_month date NOT NULL,                       -- '2026-05-01'
    due_date        date NOT NULL,
    value           numeric(15,2) NOT NULL,
    financial_record_id uuid REFERENCES financial_record(id),
    invoice_id      uuid REFERENCES invoice(id),          -- NFSe se serviço
    status          text NOT NULL CHECK (status IN ('PENDING','GENERATED','FAILED','SKIPPED')),
    generated_at    timestamptz,
    notes           text,
    UNIQUE (contract_id, competence_month)
);
CREATE INDEX idx_billing_cycle_pending ON contract_billing_cycle(competence_month) WHERE status = 'PENDING';

CREATE TABLE contract_amendment (
    id              uuid PRIMARY KEY,
    contract_id     uuid NOT NULL REFERENCES contract(id),
    type            text NOT NULL CHECK (type IN ('EXTENSION','VALUE','ITEM_ADD','ITEM_REMOVE','OTHER')),
    effective_from  date NOT NULL,
    description     text NOT NULL,
    new_end_date    date,                                  -- se EXTENSION
    new_base_value  numeric(15,2),                         -- se VALUE
    payload         jsonb,                                  -- detalhes específicos por tipo
    status          text NOT NULL CHECK (status IN ('DRAFT','EFFECTIVE','CANCELLED')),
    created_at      timestamptz NOT NULL DEFAULT now(),
    effective_at    timestamptz,
    cancelled_at    timestamptz,
    user_id         uuid NOT NULL
);

CREATE TABLE contract_adjustment (
    id              uuid PRIMARY KEY,
    contract_id     uuid NOT NULL REFERENCES contract(id),
    applied_on      date NOT NULL,
    index           text CHECK (index IN ('IGPM','IPCA','INPC','MANUAL')),
    percentage      numeric(7,4) NOT NULL,
    old_base_value  numeric(15,2) NOT NULL,
    new_base_value  numeric(15,2) NOT NULL,
    notes           text,
    user_id         uuid NOT NULL,
    UNIQUE (contract_id, applied_on)
);
```

Job de geração de billing cycles roda diariamente (com lock distribuído via `platform.shedlock`).

## Estrutura Java

```
dev.dimi.erp.contratos
├── package-info.java   ← allowedDependencies={"cadastros","produto","financeiro","fiscal","ordem-servico","shared"}
├── ContractId, BillingCycleId, AmendmentId
├── eventos:
│   ├── ContractCreatedEvent, ActivatedEvent, SuspendedEvent, TerminatedEvent
│   ├── ContractAmendedEvent, AdjustedEvent
│   ├── ContractInvoiceGeneratedEvent (gatilho financeiro + fiscal)
│   └── ContractCycleFailedEvent (gatilho alerta)
└── internal/
    ├── domain/        Contract, ContractItem, BillingCycle, Amendment, Adjustment
    ├── persistence/   *Repository
    ├── application/   CreateContractService, ActivateContractService,
    │                   SuspendContractService, TerminateContractService,
    │                   AmendContractService, AdjustContractService,
    │                   GenerateBillingCyclesJob (cron diário),
    │                   GenerateInvoiceForCycleService
    ├── web/           ContractController, AmendmentController, AdjustmentController, BillingCycleController
    └── listener/      (sem listeners de outros módulos no v1; v1.1+ pode escutar OsClosedEvent para registrar execução de manutenção)
```

## Casos de uso e endpoints

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/contratos` | `contrato.consultar` |
| `POST` | `/api/v1/contratos` | `contrato.criar` |
| `GET`  | `/api/v1/contratos/{id}` | `contrato.consultar` |
| `PATCH`| `/api/v1/contratos/{id}` | `contrato.editar` (apenas DRAFT) |
| `POST` | `/api/v1/contratos/{id}/activate` | `contrato.ativar` + step-up |
| `POST` | `/api/v1/contratos/{id}/suspend` | `contrato.suspender` + step-up |
| `POST` | `/api/v1/contratos/{id}/resume` | `contrato.suspender` + step-up |
| `POST` | `/api/v1/contratos/{id}/terminate` | `contrato.encerrar` + step-up |
| `POST` | `/api/v1/contratos/{id}/amendments` | `contrato.aditivar` + step-up |
| `POST` | `/api/v1/contratos/amendments/{id}/effective` | `contrato.aditivar` + step-up (efetiva) |
| `POST` | `/api/v1/contratos/{id}/adjustments` | `contrato.reajustar` + step-up |
| `GET`  | `/api/v1/contratos/{id}/billing-cycles` | `contrato.consultar` |
| `POST` | `/api/v1/contratos/{id}/billing-cycles/{cycleId}/regenerate` | `contrato.faturar` + step-up |
| `POST` | `/api/v1/contratos/billing-cycles/run-now?date=...` | `contrato.faturar` + step-up (job manual) |

## Eventos

```java
public record ContractCreatedEvent(ContractId id, TenantId tenantId, BranchId branchId, PersonId customerId, Money baseValue, Recurrence rec, Instant occurredAt) {}
public record ContractActivatedEvent(ContractId id, LocalDate startDate, Instant occurredAt) {}
public record ContractSuspendedEvent(ContractId id, String reason, Instant occurredAt) {}
public record ContractTerminatedEvent(ContractId id, LocalDate terminationDate, String reason, Money cancellationFee, Instant occurredAt) {}
public record ContractAmendedEvent(AmendmentId amendmentId, ContractId contractId, AmendmentType type, LocalDate effectiveFrom, Instant occurredAt) {}
public record ContractAdjustedEvent(ContractId contractId, BigDecimal percentage, Money oldValue, Money newValue, LocalDate appliedOn, Instant occurredAt) {}
public record ContractInvoiceGeneratedEvent(BillingCycleId cycleId, ContractId contractId, TenantId tenantId, BranchId branchId,
    PersonId customerId, LocalDate competenceMonth, Money value, Instant occurredAt) {}
public record ContractCycleFailedEvent(BillingCycleId cycleId, ContractId contractId, String reason, Instant occurredAt) {}
```

Consumidores:

- **`financeiro`**: `ContractInvoiceGeneratedEvent` → cria MVF CREDIT com parcelas conforme `payment_condition`. Idempotência por `(origin_type='contract', origin_id=cycleId)`.
- **`fiscal`**: opcionalmente emite NFSe quando contrato é serviço (config tenant).
- **`auditoria`**: tudo.

## Regras de negócio

### Ciclo de vida do contrato

- **RN-CTR-001**: Contrato em `DRAFT` pode ser editado livremente. Ao `activate`, edição de itens passa a exigir aditivo.
- **RN-CTR-002**: Ativação valida: items não-vazios, base_value > 0, customer ativo, payment_condition definida.
- **RN-CTR-003**: Suspensão pausa geração de novos billing cycles. Já gerados continuam.
- **RN-CTR-004**: Reativação (resume) só de `SUSPENDED`. Continua de onde parou.
- **RN-CTR-005**: Encerramento pode ser **no termo** (chega ao `end_date`), **automático por renovação** (se `auto_renew=false`, vira `EXPIRED`), **antecipado** (terminate manual, status `TERMINATED_EARLY`), ou **por inadimplência** (job futuro v1.1+ identifica e propõe encerramento).
- **RN-CTR-006**: Encerramento antecipado pode aplicar `cancellation_fee_value` como MVF a receber automaticamente.
- **RN-CTR-007**: Renovação automática gera novo período (`end_date += renewal_period_months`) na data de `end_date - cancellation_notice_days` se cliente não solicitou cancelamento.

### Billing cycles

- **RN-CTR-008**: Job diário `GenerateBillingCyclesJob`:
  - Para cada contrato `ACTIVE`, calcula próxima `competence_month` esperada.
  - Se `competence_month <= hoje + 7 dias` e ciclo ainda não gerado, gera com status `PENDING`.
  - Em seguida tenta gerar invoice + MVF (chamando `GenerateInvoiceForCycleService`).
  - Sucesso → `GENERATED`. Falha → `FAILED` + evento `ContractCycleFailedEvent` + alerta P3.
- **RN-CTR-009**: Idempotência por `(contract_id, competence_month)` — UNIQUE garante 1 ciclo por mês.
- **RN-CTR-010**: Regenerar ciclo `FAILED` é permitido (limpa e recria).
- **RN-CTR-011**: `billing_day` aplicado ao `competence_month` para gerar `due_date`. Ex: contrato com `billing_day=15` na competência `2026-05-01` tem `due_date=2026-05-15`.

### Aditivos

- **RN-CTR-012**: Aditivo `DRAFT` pode ser editado. `EFFECTIVE` aplica mudanças ao contrato (atualiza campos conforme tipo) e fica imutável.
- **RN-CTR-013**: Aditivo `EXTENSION` atualiza `end_date`.
- **RN-CTR-014**: Aditivo `VALUE` atualiza `base_value`, cria registro em `contract_adjustment` opcional (audit).
- **RN-CTR-015**: Aditivo `ITEM_ADD` / `ITEM_REMOVE` ajusta `contract_item` e recalcula `base_value` se aplicável.
- **RN-CTR-016**: Aplicação efetiva exige step-up + permission `contrato.aditivar`.

### Reajuste

- **RN-CTR-017**: Reajuste manual: operador entra percentual + índice (`IGPM`, `IPCA`, `INPC`, `MANUAL`); sistema calcula `new_base_value = old × (1 + pct)`.
- **RN-CTR-018**: Histórico mantido em `contract_adjustment` para auditoria.
- **RN-CTR-019**: Próximo reajuste calculado conforme aniversário do contrato (`start_date + 12 meses`) — campo `next_adjustment_date` populado para alerta no UI.

### Itens

- **RN-CTR-020**: Item `PRODUCT` em contrato de locação: SKU é "reservado" ao ativar contrato (v1.1+; em v1 não reservamos, apenas vinculamos). Movimentação de estoque (saída pra cliente) é manual no v1 via OS ou venda associada.
- **RN-CTR-021**: Item `SERVICE`: faturamento gera NFSe (se config). Sem efeito em estoque.

### Integração com OS (v1.1+)

- **RN-CTR-022**: Contrato pode ter flag `auto_create_maintenance_os=true` que gera OS automaticamente em cada competência (manutenção mensal). Não implementado no v1.

## Integrações

- **Módulo `cadastros`**: cliente, contato, filial, payment_condition.
- **Módulo `produto`**: SKU para itens de locação.
- **Módulo `financeiro`**: cria MVF via listener `ContractInvoiceGeneratedEvent`. Centro de custo do contrato propagado.
- **Módulo `fiscal`**: NFSe opcional para serviços recorrentes.
- **Módulo `ordem-servico`** (v1.1+): geração automática de OS para contratos de manutenção.
- **Módulo `auditoria`**: tudo.

## Critérios de aceitação

- **Dado** contrato `DRAFT` com 1 item de serviço, `base_value=1000`, recurrence `MONTHLY`, `billing_day=10`, `start_date=2026-05-01`, **quando** ativado, **então** status `ACTIVE`, evento `ContractActivatedEvent`, próximo ciclo esperado em `2026-05-10`.
- **Dado** contrato ativo, **quando** job diário roda em `2026-05-08`, **então** ciclo `2026-05-01` é gerado com status `GENERATED`, MVF a receber R$ 1000 criada com vencimento `2026-05-10`.
- **Dado** contrato com 12 ciclos gerados e `end_date=2026-12-31`, **quando** chega janeiro/2027, **então** status `EXPIRED` se `auto_renew=false`; senão renovação acontece em `end_date - cancellation_notice_days`.
- **Dado** aditivo `EXTENSION` com novo `end_date`, **quando** efetivado, **então** contrato.end_date atualizado, evento publicado, audit log.
- **Dado** aditivo `VALUE` com `new_base_value=1100`, **quando** efetivado em `2026-06-01`, **então** ciclos a partir de junho usam 1100; ciclos anteriores intactos.
- **Dado** reajuste IGPM 5% em `2026-12-15`, **quando** aplicado, **então** base_value antigo 1000 → novo 1050, registro em `contract_adjustment`.
- **Dado** encerramento antecipado com cancellation_fee 500, **quando** terminate, **então** status `TERMINATED_EARLY`, MVF "Multa contratual" 500 criada, evento `ContractTerminatedEvent`.
- **Dado** ciclo `FAILED` (NFSe falhou), **quando** regenerate, **então** ciclo limpo e refeito (idempotente).

## Não-funcionais

- **Job diário**: < 60s para 1000 contratos ativos (gera ~30 ciclos em média, considerando recorrência mensal).
- **Latência POST contrato**: p95 < 200ms.
- **Latência ativação**: p95 < 300ms.
- **Idempotência**: 100% — re-rodar o job não duplica ciclos.

## Roadmap

- **v1.1**: geração automática de OS de manutenção (`auto_create_maintenance_os`); índices automáticos via API IBGE/FGV; alerta de vencimento próximo + email cliente; assinatura eletrônica via Clicksign/DocuSign integration.
- **v1.2**: contratos com fornecedor (compras recorrentes); split de pagamento; cobrança recorrente via gateway (cartão de crédito mensal); workflow multi-aprovação para aditivos.
- **v2**: análise de churn; previsão de cancelamento; renegociação assistida.
