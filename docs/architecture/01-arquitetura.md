# 01 — Arquitetura

Este doc define **como o código é organizado, como módulos se comunicam, e quais padrões usamos por construção**. Tudo o mais (segurança, multi-tenancy, observabilidade) constrói em cima destas decisões.

## Princípios

1. **Bounded contexts isolados** — cada módulo é um pedaço autocontido com vocabulário próprio. Acoplamento entre módulos é explícito e pequeno.
2. **Dependências apontam para o domínio** — domínio puro no centro, adapters de infraestrutura na borda. Hexagonal por construção.
3. **Comunicação assíncrona por padrão** — eventos in-process via Spring Modulith + Outbox. Síncrona só quando a transação precisa ser atômica.
4. **Imutabilidade onde faz sentido** — Value Objects como records, DTOs como records, IDs tipados.
5. **Sem mágica escondida** — ArchUnit + Modulith tests falham CI quando alguém quebra a topologia.
6. **Monolito modular, não microserviços** — todo o v1 num único deployable. Bounded contexts ficam separados *internamente*; externalização para serviços vem só quando justificada por carga ou time.

## Bounded contexts do Modular ERP POC

```
dev.dimi.erp
├── shared               (infraestrutura compartilhada — Value Objects, exceções, validators)
├── identidade           (tenant, usuário, autenticação, RBAC, MFA, audit hooks)
├── cadastros            (pessoa, filial, funcionário, catálogos)
├── produto              (produto, variedade, embalagem, NCM/CEST, tabelas de preço)
├── estoque              (saldo, ledger, movimentos, lotes, locais, WAC)
├── movimentacao         (pedido, venda, devolução, transferência, ajuste)
├── compras              (SC, cotação, PC, recebimento)
├── financeiro           (plano de contas, centro de custo, MVF, parcelas, pagamentos)
├── fiscal               (invoice, tax_calculation, FiscalProvider port, ACBr adapter)
├── ordemservico         (OS, itens, workflow, faturamento misto)
├── contratos            (contrato, recorrência, aditivos, vencimentos)
└── auditoria            (audit log unificado, retenção, anonimização LGPD)
```

Cada módulo é um pacote raiz em `dev.dimi.erp.<modulo>`. O `package-info.java` da raiz declara o módulo:

```java
@org.springframework.modulith.ApplicationModule(
    displayName = "Estoque",
    allowedDependencies = { "produto", "shared" }
)
package dev.dimi.erp.estoque;
```

`allowedDependencies` é a **única** porta declarada — tentativas de importar módulos não listados falham `ApplicationModules.verify()` no teste.

## Estrutura interna de um módulo

Cada bounded context segue o mesmo layout:

```
dev.dimi.erp.<modulo>
├── package-info.java              ← @ApplicationModule + allowedDependencies
├── <Modulo>Id.java                ← record tipado para o ID raiz (ex: ProductId, InvoiceId)
├── <Eventos>.java                 ← events publicados (records imutáveis)
├── <ServicesPublicos>.java        ← interfaces de service expostas para outros módulos
└── internal/                      ← TUDO o que não é API pública
    ├── domain/                    ← entidades JPA, Value Objects internos, aggregates
    ├── persistence/               ← Spring Data repositories
    ├── application/               ← services de aplicação, command handlers
    ├── web/                       ← controllers REST, DTOs de fronteira
    ├── adapter/                   ← adapters para portas (ACBr, S3, etc)
    └── listener/                  ← consumers de eventos de outros módulos
```

**Regras estritas**:

1. **Nada em `internal/` é importável de fora do módulo.** ArchUnit garante isso.
2. **A API pública (na raiz do pacote)** contém apenas: eventos, IDs tipados, interfaces de service exposto, exceções de domínio expostas.
3. **DTOs de fronteira REST** ficam em `internal/web/dto/` e **não** são reaproveitados como objetos de domínio.
4. **Entidades JPA** ficam em `internal/domain/` e **nunca** atravessam o módulo — sempre virar DTO/record na fronteira.

## Camadas dentro do módulo

Hexagonal simplificado:

```
        ┌──────────────────────────────────────┐
        │    web (controllers, DTOs REST)      │ ← entrada HTTP
        ├──────────────────────────────────────┤
        │    listener (event consumers)        │ ← entrada eventos
        ├──────────────────────────────────────┤
        │    application (services, commands)  │ ← orquestração + transações
        ├──────────────────────────────────────┤
        │    domain (entities, VOs, regras)    │ ← núcleo puro
        ├──────────────────────────────────────┤
        │    persistence (repositories)        │ ← saída banco
        │    adapter (ACBr, S3, SMTP, etc)     │ ← saída sistemas externos
        └──────────────────────────────────────┘
```

- **Domain** não conhece Spring nem JPA. Entidades JPA vivem em `domain/` por pragmatismo (evita duplicar para "domain pura" + "entity") mas as classes não dependem de Spring; só de anotações JPA/Validation.
- **Application services** são `@Service` Spring, com `@Transactional` por método público quando apropriado.
- **Web layer** valida entrada (Jakarta Validation), traduz DTO ↔ comando, chama application service, traduz resultado para DTO de resposta.
- **Persistence** são `JpaRepository<E, Id>` Spring Data com queries via Specifications quando dinâmicas.
- **Adapter** implementa interfaces (ports) declaradas em `application/` para sistemas externos.

## Comunicação entre módulos

### Síncrona — service exposto

Use quando: **a transação precisa ser atômica entre módulos** (raríssimo) ou **o módulo precisa de uma resposta imediata** (consultas).

Exemplo: Movimentação consulta estoque disponível antes de criar venda.

```java
// Em estoque (API pública):
public interface StockQueryPort {
    StockSnapshot getSnapshot(ProductId productId, BranchId branchId);
}

// Em movimentacao:
@Service
public class CreateSaleService {
    private final StockQueryPort stockQuery;  // injetado, módulo estoque
    ...
}
```

Restrições:
- Só `interfaces` na API pública, nunca classes concretas.
- Implementação fica em `estoque/internal/application/`.
- Module `movimentacao` declara `allowedDependencies = { "estoque", ... }`.

### Assíncrona — eventos de domínio (padrão)

Use quando: **mudança em um módulo precisa ser propagada para outros**, e *não* exige atomicidade. **Esta é a regra geral.**

Exemplo: Venda fechada → Estoque baixa + Financeiro cria MVF a receber + Fiscal emite NFe + Auditoria registra.

```java
// Em movimentacao (API pública):
public record SaleClosedEvent(
    SaleId saleId,
    TenantId tenantId,
    BranchId branchId,
    PersonId customerId,
    List<SaleLine> lines,
    Money totalAmount,
    Instant occurredAt
) {}

// Em movimentacao/internal/application:
public class CloseSaleService {
    private final ApplicationEventPublisher events;

    @Transactional
    public void close(SaleId id) {
        // ... lógica de fechamento ...
        events.publishEvent(new SaleClosedEvent(...));
    }
}

// Em estoque/internal/listener:
@Component
class SaleStockListener {
    @ApplicationModuleListener
    void on(SaleClosedEvent event) {
        // baixa estoque
    }
}
```

### Como funcionam os eventos via Spring Modulith

- `ApplicationEventPublisher.publishEvent(...)` enfileira o evento em memória.
- No **commit da transação atual**, o evento é **persistido em `event_publication`** (tabela default do Modulith no schema do tenant ativo).
- Após commit, listeners `@ApplicationModuleListener` são executados em **transação separada** (cada listener tem sua própria).
- Falha em listener → `event_publication.completed_at` continua null → reprocessamento periódico via job (`incompleteEventPublications`).
- Sucesso → `completed_at` setado → linha purgada após retenção configurada.

Garantia: **at-least-once delivery**. Listeners precisam ser **idempotentes** (verificar se já processaram aquele `event_publication_id`).

## Outbox in-process

A combinação `event_publication` (Modulith) + commit transacional + processamento pós-commit **É** o padrão Outbox. Diferença para Outbox tradicional:

| Aspecto | Outbox tradicional (broker) | Modulith in-process |
|---|---|---|
| Publicação | Linha na tabela `outbox`, dispatcher polling envia para Kafka | Linha em `event_publication`, listener chamado direto |
| Delivery | At-least-once para consumers em qualquer serviço | At-least-once para listeners no mesmo processo |
| Externalização | Já está externalizado | Não — listeners no mesmo JVM |
| Custo operacional | Broker para administrar | Zero infra extra |

**Quando externalizar**: módulo virar serviço separado por escala/time, OU integrações externas precisarem consumir eventos. Hoje, externalização zero — tudo no monolith modular.

## Sagas para operações cross-module

Cancelamentos em cascata (estorno) seguem **Saga com compensação por eventos**. Exemplo: cancelar venda.

```
                  SaleCancellationRequested
                          ↓
       ┌──────────────────┼──────────────────┐
       ↓                  ↓                  ↓
   Estoque            Financeiro         Fiscal
   (devolve)          (estorna MVF)      (cancela NFe)
       ↓                  ↓                  ↓
   StockReversed     ReceivableReversed  InvoiceCancelled
       └──────────────────┼──────────────────┘
                          ↓
              SaleCancellationCompleted
                       (ou failed)
```

- Cada step é independente, idempotente, e emite evento ao concluir.
- Coordenador (em `movimentacao`) escuta os 3 eventos de conclusão e fecha a Saga.
- Se um step falha permanentemente: estado `FALHA_PARCIAL_CANCELAMENTO` + alerta para operador humano resolver.
- **Nunca** rollback distribuído. Compensação > 2PC.

Detalhes operacionais por módulo nos docs de domínio (ver [docs/domain/](../domain/)).

## Padrão de transação

- **`@Transactional` na borda da application layer** (services), nunca em controllers, nunca em repositories.
- **Propagação default** `REQUIRED`. Use `REQUIRES_NEW` apenas em audit log (não pode ser revertido pelo rollback do caller).
- **Read-only** explícito em queries: `@Transactional(readOnly = true)`.
- **Listeners** `@ApplicationModuleListener` recebem transação própria (`REQUIRES_NEW`).
- **Pessimistic lock** (`SELECT FOR UPDATE`) em operações de estoque (saldo, ledger) e séries fiscais — concorrência crítica.
- **Optimistic lock** (`@Version`) em entidades de leitura concorrente sem write contention alta (pessoa, produto, contrato).

## IDs tipados

Cada agregado tem ID próprio como record:

```java
public record InvoiceId(UUID value) {
    public InvoiceId {
        Objects.requireNonNull(value, "invoice id");
    }
    public static InvoiceId newId() {
        return new InvoiceId(UuidCreator.getTimeOrderedEpoch());
    }
}
```

Benefícios:
- Type-safe: `void cancelInvoice(InvoiceId)` impossível chamar com `PersonId`.
- Documenta intenção na assinatura.
- Não muda performance (compile-time).

Jackson serializa o record como string (UUID) quando configurado com `@JsonValue` no `value()`. Hibernate persiste como `uuid` via `AttributeConverter`.

## Value Objects

Tipos de domínio com validação no construtor, imutáveis:

```java
public record Cpf(String value) {
    public Cpf {
        Objects.requireNonNull(value);
        String digits = value.replaceAll("\\D", "");
        if (!isValid(digits)) throw new InvalidCpfException(value);
        value = digits;
    }
    private static boolean isValid(String digits) { /* DV */ }
}
```

Pares conhecidos: `Cpf`, `Cnpj`, `Cep`, `Email`, `PhoneNumber`, `Money` (com scale 2 ou 4), `Percentage`, `Quantity`.

Vivem em `dev.dimi.erp.shared.vo` quando reutilizáveis, ou em `<modulo>/internal/domain/vo/` quando específicos.

## DTOs e records

- **DTOs de entrada** (requisição REST): records com Jakarta Validation. Nome: `Create<X>Request`, `Update<X>Request`, `<X>Filter`.
- **DTOs de saída** (resposta REST): records imutáveis. Nome: `<X>Response`, `<X>Summary`, `<X>Detail`.
- **Commands** (intra-application): records imutáveis. Nome: `<Verb><X>Command`.
- **Events** (entre módulos): records imutáveis na API pública. Nome: `<X><Verb>Event` no past tense (`SaleClosedEvent`, `InvoiceAuthorizedEvent`).

Conversão entre records é manual ou via MapStruct (quando ≥5 campos repetitivos).

## Como adicionar um módulo novo

1. Criar pacote `dev.dimi.erp.<modulo>` com `package-info.java` declarando `@ApplicationModule` e `allowedDependencies`.
2. Criar subpacote `internal/` com `domain/`, `persistence/`, `application/`, `web/`, `adapter/`, `listener/` conforme precisar.
3. Adicionar entidades JPA. Se o módulo persiste dados, criar migration Flyway `V<n>__<modulo>_<descricao>.sql` (aplicada **por tenant** no provisioning).
4. Adicionar testes ArchUnit em `src/test/java/.../arch/` que validem que `internal/` é privado.
5. Adicionar teste Modulith `@ApplicationModuleTest` para o módulo.
6. Atualizar `docs/domain/<numero>-<modulo>.md` com escopo, eventos, regras, etc.

## ArchUnit — regras vigentes

Em `src/test/java/dev/dimi/erp/arch/ArchitectureTest.java`:

```java
@AnalyzeClasses(packages = "dev.dimi.erp")
class ArchitectureTest {

    @ArchTest
    static final ArchRule internal_nao_e_acessado_de_fora_do_modulo =
        classes().that().resideInAPackage("..internal..")
            .should().onlyBeAccessed().byClassesThat()
                .resideInAPackage("..internal..").orShould().resideInTheSamePackage();

    @ArchTest
    static final ArchRule controllers_nao_chamam_repositories =
        noClasses().that().resideInAPackage("..web..")
            .should().dependOnClassesThat().resideInAPackage("..persistence..");

    @ArchTest
    static final ArchRule entidades_jpa_nao_atravessam_modulo =
        classes().that().areAnnotatedWith(Entity.class)
            .should().resideInAPackage("..internal.domain..");

    @ArchTest
    static final ArchRule services_publicos_sao_interfaces =
        classes().that().resideInAnyPackage("dev.dimi.erp.(*)")
            .and().haveSimpleNameEndingWith("Port").or().haveSimpleNameEndingWith("Service")
            .should().beInterfaces();
}
```

## Modulith — verificação

Em `src/test/java/.../ModuleStructureTest.java`:

```java
class ModuleStructureTest {

    ApplicationModules modules = ApplicationModules.of(ModularErpPocApplication.class);

    @Test
    void valida_topologia_dos_modulos() {
        modules.verify();  // falha se algum módulo importa fora dos allowedDependencies
    }

    @Test
    void gera_documentacao() {
        new Documenter(modules)
            .writeModulesAsPlantUml()
            .writeIndividualModulesAsPlantUml();
    }
}
```

Documentação gerada vai para `target/spring-modulith-docs/` e pode ser commitada como referência viva.

## Quando o monolith vira microserviços

Critérios objetivos para externalizar um módulo:

1. **Carga isolada**: módulo demanda >2x recursos do resto. Ex: fiscal saturando CPU/memória em emissões em massa.
2. **Time independente**: equipe própria com cadência de release distinta.
3. **Stack divergente**: módulo precisaria de stack diferente (raramente justifica).
4. **Resiliência**: módulo crítico precisa isolamento de falha (cardiac arrest do resto não pode derrubar fiscal).

**Não** externalize por estética ou por "best practice" — monolith modular **bem feito** escala muito além do que a maioria dos produtos precisa. Quando externalizar, eventos viram pub/sub via Kafka/RabbitMQ e API pública vira REST/gRPC — *sem reescrita* do domínio.

## Anti-padrões a evitar

- **Service de 5000+ linhas** que orquestra tudo. Quebrar em command handlers menores por caso de uso.
- **Entidade JPA viajando entre módulos** como API. Sempre traduzir para DTO/record na fronteira.
- **Listeners reagindo a eventos de muitos módulos** (god listener). Listeners pequenos, um por intenção.
- **Lógica de domínio em PL/pgSQL**. Lógica fica em Java; SQL é só para queries.
- **Repositories chamando outros repositories** — deveria ser service.
- **Controllers fazendo `@Transactional`** — transação pertence ao application layer.
- **Eventos como wrapper de comando** ("UpdatePersonRequested"). Eventos descrevem **fato consumado**, não pedido.

## Decisões registradas

- **ADR-001**: Schema-per-tenant em Postgres ([03-multi-tenancy.md](03-multi-tenancy.md)).
- **ADR-002**: Spring Modulith + Outbox in-process para v1 (este doc).
- **ADR-003**: Argon2id para hashes novos ([02-seguranca.md](02-seguranca.md)).
- **ADR-004**: ACBr externo como sidecar para fiscal ([../domain/13-fiscal.md](../domain/13-fiscal.md)).
- **ADR-005**: IDs tipados como records em vez de UUID cru (este doc).
