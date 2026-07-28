# 16 — Ordem de Serviço

Módulo `ordemservico` — gestão de Ordens de Serviço (OS) para empresas de serviço, manutenção, instalação, assistência técnica. Faturamento misto NFe (produtos consumidos) + NFSe (serviço executado).

## Escopo

**Cobre**: OS com workflow configurável por tenant (fase + status), itens de OS (produto consumido + serviço executado na mesma OS), apontamento de mão-de-obra (horas/técnicos), recursos alocados, faturamento parcial ou total, geração simultânea de NFe e NFSe, encerramento.

**Não cobre v1**: Kanban visual (UI); formulários customizados dinâmicos; checklists de execução; SLA com escalonamento; gestão de garantia automatizada; integração com sistemas de campo (mobile workers).

## Conceitos centrais

- **Ordem de Serviço (`service_order`)**: documento principal. Cliente, contato, equipamento (opcional), filial, status, datas, valores.
- **Item de produto (`service_order_product`)**: SKU consumido na execução. Baixa estoque ao iniciar (ou ao executar — configurável). Vai como item de NFe no faturamento.
- **Item de serviço (`service_order_service`)**: serviço prestado, com código de serviço municipal, alíquota ISSQN, valor. Vai como item de NFSe.
- **Apontamento (`service_order_labor`)**: registro de mão-de-obra. Funcionário, horas, descrição. Pode ter custo interno (não é faturável necessariamente).
- **Workflow (`os_workflow_status`)**: estados configuráveis por tenant. Default: `OPEN`, `IN_PROGRESS`, `AWAITING_PARTS`, `READY`, `BILLED`, `CLOSED`, `CANCELLED`.
- **Faturamento**: emite NFe para produtos consumidos + NFSe para serviços. Pode ser parcial (medição) ou total (encerramento).

## Modelo de dados

```sql
CREATE TABLE os_workflow_status (
    id              uuid PRIMARY KEY,
    code            text NOT NULL UNIQUE,
    display_name    text NOT NULL,
    is_initial      boolean NOT NULL DEFAULT false,
    is_terminal     boolean NOT NULL DEFAULT false,
    is_billable     boolean NOT NULL DEFAULT false,
    is_system       boolean NOT NULL DEFAULT false,
    sort_order      int NOT NULL DEFAULT 0
);

CREATE TABLE os_workflow_transition (
    from_status_id  uuid NOT NULL REFERENCES os_workflow_status(id),
    to_status_id    uuid NOT NULL REFERENCES os_workflow_status(id),
    required_permission text,
    PRIMARY KEY (from_status_id, to_status_id)
);

CREATE TABLE service_order (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    number          bigint NOT NULL,
    branch_id       uuid NOT NULL REFERENCES branch(id),
    customer_id     uuid NOT NULL REFERENCES person(id),
    contact_id      uuid REFERENCES person_contact(id),
    contract_id     uuid,                            -- v1.1+: OS gerada por contrato
    equipment_description text,
    serial_number   text,
    problem_reported text,
    diagnosis       text,
    solution        text,

    status_id       uuid NOT NULL REFERENCES os_workflow_status(id),
    priority        text CHECK (priority IN ('LOW','NORMAL','HIGH','URGENT')) DEFAULT 'NORMAL',

    opened_at       timestamptz NOT NULL DEFAULT now(),
    started_at      timestamptz,
    finished_at     timestamptz,
    billed_at       timestamptz,
    closed_at       timestamptz,
    cancelled_at    timestamptz,

    payment_condition_id uuid REFERENCES catalog_payment_condition(id),

    total_products  numeric(15,2) NOT NULL DEFAULT 0,
    total_services  numeric(15,2) NOT NULL DEFAULT 0,
    total_discount  numeric(15,2) NOT NULL DEFAULT 0,
    total           numeric(15,2) NOT NULL DEFAULT 0,

    notes           text,
    created_by      uuid NOT NULL,
    assigned_to     uuid,                            -- técnico responsável (employee)
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),
    UNIQUE (branch_id, number)
);
CREATE INDEX idx_os_status ON service_order(status_id) WHERE closed_at IS NULL;
CREATE INDEX idx_os_assigned ON service_order(assigned_to) WHERE closed_at IS NULL;

CREATE TABLE service_order_product (
    id              uuid PRIMARY KEY,
    os_id           uuid NOT NULL REFERENCES service_order(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    packaging_id    uuid NOT NULL REFERENCES packaging(id),
    quantity        numeric(15,4) NOT NULL,
    unit_price      numeric(15,4) NOT NULL,
    discount_value  numeric(15,2) NOT NULL DEFAULT 0,
    net_value       numeric(15,2) NOT NULL,
    consumed_at     timestamptz,                     -- quando estoque foi baixado
    UNIQUE (os_id, line_number)
);

CREATE TABLE service_order_service (
    id              uuid PRIMARY KEY,
    os_id           uuid NOT NULL REFERENCES service_order(id) ON DELETE CASCADE,
    line_number     int NOT NULL,
    service_code    text NOT NULL,                   -- código municipal LC 116
    description     text NOT NULL,
    quantity        numeric(15,4) NOT NULL DEFAULT 1,
    unit_price      numeric(15,4) NOT NULL,
    discount_value  numeric(15,2) NOT NULL DEFAULT 0,
    net_value       numeric(15,2) NOT NULL,
    issqn_rate      numeric(7,4) NOT NULL,
    issqn_value     numeric(15,2) NOT NULL DEFAULT 0,
    pis_cofins_calc jsonb,
    UNIQUE (os_id, line_number)
);

CREATE TABLE service_order_labor (
    id              uuid PRIMARY KEY,
    os_id           uuid NOT NULL REFERENCES service_order(id) ON DELETE CASCADE,
    employee_id     uuid NOT NULL REFERENCES employee(id),
    started_at      timestamptz NOT NULL,
    finished_at     timestamptz,
    hours           numeric(7,2),
    description     text,
    internal_cost   numeric(15,2),                   -- custo interno (não vai pra fatura necessariamente)
    is_billable     boolean NOT NULL DEFAULT false,
    related_service_line int,                        -- vincula a service_order_service
    created_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE service_order_billing (
    id              uuid PRIMARY KEY,
    os_id           uuid NOT NULL REFERENCES service_order(id),
    billing_type    text NOT NULL CHECK (billing_type IN ('PARTIAL','FINAL')),
    issued_at       timestamptz NOT NULL DEFAULT now(),
    nfe_invoice_id  uuid REFERENCES invoice(id),     -- NFe dos produtos
    nfse_invoice_id uuid REFERENCES invoice(id),     -- NFSe dos serviços
    products_amount numeric(15,2) NOT NULL DEFAULT 0,
    services_amount numeric(15,2) NOT NULL DEFAULT 0,
    total           numeric(15,2) NOT NULL,
    notes           text,
    user_id         uuid NOT NULL
);

CREATE TABLE service_order_status_history (
    id              uuid PRIMARY KEY,
    os_id           uuid NOT NULL REFERENCES service_order(id) ON DELETE CASCADE,
    from_status_id  uuid REFERENCES os_workflow_status(id),
    to_status_id    uuid NOT NULL REFERENCES os_workflow_status(id),
    changed_at      timestamptz NOT NULL DEFAULT now(),
    changed_by      uuid NOT NULL,
    notes           text
);
```

Seed inicial dos status (`is_system=true`): `OPEN`, `IN_PROGRESS`, `AWAITING_PARTS`, `READY`, `BILLED`, `CLOSED`, `CANCELLED`. Tenant pode adicionar status customizados (ex: `AWAITING_APPROVAL`) e definir transições.

## Estrutura Java

```
dev.dimi.erp.ordemservico
├── package-info.java   ← allowedDependencies={"cadastros","produto","estoque","fiscal","financeiro","shared"}
├── ServiceOrderId, BillingId
├── eventos:
│   ├── OsOpenedEvent, OsStatusChangedEvent
│   ├── OsProductConsumedEvent (gatilho estoque)
│   ├── OsBilledEvent (gatilho fiscal + financeiro)
│   ├── OsClosedEvent, OsCancelledEvent
│   └── LaborAppointedEvent
└── internal/
    ├── domain/        ServiceOrder, OsProduct, OsService, OsLabor, OsBilling, WorkflowStatus
    ├── persistence/   *Repository
    ├── application/   OpenOsService, ChangeStatusService, AddProductService, AddServiceService,
    │                   AppointLaborService, BillOsService, CloseOsService, CancelOsService
    ├── web/           ServiceOrderController, WorkflowStatusController, BillingController, LaborController
    └── listener/      (sem listeners externos obrigatórios)
```

## Casos de uso e endpoints

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/ordens-servico` | `os.consultar` |
| `POST` | `/api/v1/ordens-servico` | `os.criar` |
| `GET`  | `/api/v1/ordens-servico/{id}` | `os.consultar` |
| `PATCH`| `/api/v1/ordens-servico/{id}` | `os.editar` |
| `POST` | `/api/v1/ordens-servico/{id}/transitions` | varia por transition (mapeada para `required_permission`) |
| `POST` | `/api/v1/ordens-servico/{id}/products` | `os.editar` |
| `POST` | `/api/v1/ordens-servico/{id}/services` | `os.editar` |
| `POST` | `/api/v1/ordens-servico/{id}/labors` | `os.apontar-mao-obra` |
| `PATCH`| `/api/v1/ordens-servico/{id}/labors/{laborId}/finish` | `os.apontar-mao-obra` |
| `POST` | `/api/v1/ordens-servico/{id}/bill` | `os.faturar` (`Idempotency-Key` obrigatório; tipo `PARTIAL` ou `FINAL`) |
| `POST` | `/api/v1/ordens-servico/{id}/close` | `os.fechar` |
| `POST` | `/api/v1/ordens-servico/{id}/cancel` | `os.cancelar` + step-up |
| `GET`  | `/api/v1/ordens-servico/workflow/statuses` | `os.consultar` (catálogo de status do tenant) |
| `POST` | `/api/v1/ordens-servico/workflow/statuses` | `os.configurar-workflow` + step-up |
| `POST` | `/api/v1/ordens-servico/workflow/transitions` | `os.configurar-workflow` + step-up |

## Eventos

```java
public record OsOpenedEvent(ServiceOrderId id, TenantId tenantId, BranchId branchId, PersonId customerId, Instant occurredAt) {}
public record OsStatusChangedEvent(ServiceOrderId id, UUID fromStatusId, UUID toStatusId, UserId by, Instant occurredAt) {}
public record OsProductConsumedEvent(ServiceOrderId osId, PackagingId packagingId, BigDecimal qty, Instant occurredAt) {}
public record LaborAppointedEvent(UUID laborId, ServiceOrderId osId, EmployeeId employeeId, BigDecimal hours, Instant occurredAt) {}
public record OsBilledEvent(BillingId billingId, ServiceOrderId osId, TenantId tenantId, BranchId branchId,
    PersonId customerId, Money totalProducts, Money totalServices, BillingType type, Instant occurredAt) {}
public record OsClosedEvent(ServiceOrderId id, Instant occurredAt) {}
public record OsCancelledEvent(ServiceOrderId id, String reason, Instant occurredAt) {}
```

Consumidores:

- **`estoque`**: `OsProductConsumedEvent` → OUTBOUND síncrono (ou via port direto na transação da operação).
- **`fiscal`**: `OsBilledEvent` → emite NFe (produtos) e/ou NFSe (serviços) conforme valores não-zero.
- **`financeiro`**: `OsBilledEvent` → cria MVF a receber com parcelas conforme `payment_condition`.
- **`auditoria`**: tudo.

## Regras de negócio

### Workflow

- **RN-OS-001**: Tenant configura status customizados (com flag `is_billable` para indicar se OS pode ser faturada no status). Status `is_system=true` não pode ser deletado.
- **RN-OS-002**: Transição entre status valida `os_workflow_transition` — transições não cadastradas são bloqueadas.
- **RN-OS-003**: Permissão por transição via `required_permission` opcional.
- **RN-OS-004**: Histórico de status em `service_order_status_history`.

### Itens

- **RN-OS-005**: Produto adicionado à OS pode ser **consumido imediatamente** (baixa estoque) ou apenas previsto. Comportamento configurável (`tenant_setting.os_consume_on_add=true|false`). Default: ao mudar para status `IN_PROGRESS`.
- **RN-OS-006**: Serviço tem `service_code` (LC 116 município), alíquota ISSQN do município da filial.
- **RN-OS-007**: Mão-de-obra (`labor`) é registro interno — pode ser faturável ou apenas para custo. Quando `is_billable=true` e `related_service_line` aponta para um service line, é considerado componente do serviço; senão fica como informativo.
- **RN-OS-008**: Edição de OS após `BILLED` é bloqueada para itens; observações livres permitidas.

### Faturamento

- **RN-OS-009**: Faturamento parcial é permitido — só itens não-ainda-faturados são incluídos. Cada item tem flag implícita "já faturado em billing X".
- **RN-OS-010**: Faturamento final inclui resto + muda status para `BILLED`.
- **RN-OS-011**: NFe + NFSe são geradas **na mesma transação** (Saga local) — falha em qualquer impede o billing. Idempotência por `Idempotency-Key`.
- **RN-OS-012**: Quando OS tem só produtos → só NFe; só serviços → só NFSe; ambos → ambos.
- **RN-OS-013**: Se `total_products = 0`, NFe não é emitida (esquema fiscal não permite NFe com valor zero).

### Cancelamento

- **RN-OS-014**: Cancelamento de OS antes de faturamento: apenas marca status `CANCELLED`, estorna produtos consumidos (REVERSAL no estoque). Sem efeitos fiscais.
- **RN-OS-015**: Cancelamento após faturamento dispara Saga: cancela NFe + NFSe + estorna MVF + estorna estoque. Se NFe já estiver fora do prazo de cancelamento (SEFAZ), Saga falha → status `PARTIAL_CANCELLATION_FAILED` + alerta.
- **RN-OS-016**: Step-up obrigatório para cancelamento + permission `os.cancelar`.

### Encerramento

- **RN-OS-017**: `closed_at` é setado em transição para `CLOSED`. Após, OS é read-only (nem comentários adicionados).

## Integrações

- **Módulo `cadastros`**: cliente, contato, funcionário (técnico), filial.
- **Módulo `produto`**: SKU consumido.
- **Módulo `estoque`**: OUTBOUND ao consumir produto; REVERSAL ao cancelar.
- **Módulo `fiscal`**: NFe + NFSe gerados no billing.
- **Módulo `financeiro`**: MVF a receber gerada via listener `OsBilledEvent`.
- **Módulo `contratos`** (v1.1+): OS pode ser gerada automaticamente a partir de contrato de manutenção recorrente.
- **Módulo `auditoria`**: tudo.

## Critérios de aceitação

- **Dado** uma OS aberta com diagnóstico, **quando** transição para `IN_PROGRESS`, **então** status muda, produtos previstos viram consumidos (se `os_consume_on_add=true` ou regra equivalente), eventos `OsStatusChangedEvent` + N `OsProductConsumedEvent` publicados.
- **Dado** uma OS com 2 produtos + 1 serviço, **quando** billing FINAL, **então** NFe com 2 itens + NFSe com 1 item são emitidas, MVF a receber criada, status vai para `BILLED`.
- **Dado** OS faturada parcialmente (apenas o serviço), **quando** billing PARTIAL, **então** NFSe é emitida; NFe não (sem produtos pendentes ainda); billing seguinte cobrirá os produtos.
- **Dado** uma OS no status `IN_PROGRESS`, **quando** cancelar com motivo + step-up, **então** estoque consumido é estornado, status `CANCELLED`, sem NFe (não há ainda).
- **Dado** uma OS `BILLED` dentro do prazo, **quando** cancelar, **então** Saga cancela NFe+NFSe+MVF+estorna estoque. Status final `CANCELLED`.
- **Dado** OS faturada e NFe fora do prazo de cancelamento SEFAZ, **quando** cancelar, **então** Saga falha no step fiscal, status `PARTIAL_CANCELLATION_FAILED`, alerta para operador.

## Não-funcionais

- **Latência POST OS**: p95 < 200ms.
- **Latência billing**: p95 < 4s (envolve NFe + NFSe + MVF; depende de ACBr/prefeitura).
- **Volume**: 100 OS abertas/dia por filial sem degradação.
- **Histórico de status**: < 50 entradas típico por OS — sem index extra precisa.

## Roadmap

- **v1.1**: OS automática a partir de contrato (manutenção mensal); checklist de execução; geração de orçamento (rascunho de OS) com aprovação do cliente.
- **v1.2**: aplicação mobile para técnicos (apontamento em campo); fotos anexas; assinatura digital do cliente.
- **v2**: SLA com escalonamento; gestão de garantia automatizada; planning de agendamento (calendar).
