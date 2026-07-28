# 11 — Cadastros

Módulo `cadastros` — entidades-base reaproveitadas por todos os demais módulos: pessoas (PF/PJ), filiais (com dados fiscais), funcionários, catálogos auxiliares.

## Escopo

**Cobre**: pessoa polimórfica (PF/PJ com modalidades cliente/fornecedor/transportadora/vendedor), endereços, contatos, filial (unidade operacional com CNPJ próprio e parâmetros fiscais), funcionário (vínculo pessoa + filial + cargo), catálogos (segmento, região, tipo de endereço, tipo de contato, cargo, departamento, unidade de medida, modalidades, condição de pagamento), anonimização LGPD.

**Não cobre**: usuários do sistema (`identidade`), produtos (`produto`), parâmetros fiscais cross-tenant (em `shared`), folha de pagamento (descartado).

### Capacidades

- Value Objects validados (`Cpf`, `Cnpj`, `Cep`) e serviços de cadastro de pessoas.
- CRUD de filiais, funcionários e catálogos auxiliares, com query ports para outros módulos.
- Anonimização e exportação de dados pessoais protegidas por step-up.
- Consulta opcional de CEP por adapter HTTP.
- Gestão de certificado A1 por backend de segredos configurável, com extração PKCS12 e verificação de expiração.

## Conceitos centrais

- **Pessoa (`person`)**: PF ou PJ. Polimórfica via campo `type`. Pode ter múltiplas **modalidades** simultâneas (`CUSTOMER`, `SUPPLIER`, `CARRIER`, `SELLER`).
- **Modalidade (`person_modality`)**: marcação ativa/inativa do papel da pessoa. Permite mesma pessoa ser cliente E fornecedor sem duplicação.
- **Endereço (`person_address`)**: 0..N por pessoa, um marcado como padrão (`is_default=true`).
- **Contato (`person_contact`)**: 0..N por pessoa (email, telefone, etc).
- **Filial (`branch`)**: unidade operacional do tenant. Tem código único, indica se é matriz (`is_main=true`), opcionalmente CNPJ próprio.
- **Dados fiscais de filial (`branch_fiscal`)**: CNPJ próprio, regime tributário (Simples/Lucro Presumido/Real/MEI), inscrições estadual/municipal, certificado A1 (referência ao secret no Azure Key Vault). Separado em tabela 1:1 com `branch` para isolar dados sensíveis e simplificar permissionamento.
- **Funcionário (`employee`)**: vínculo `(person, branch)` com cargo, departamento, datas de admissão/desligamento.
- **Catálogos**: tabelas auxiliares com registros editáveis pelo tenant (com seed inicial).

## Modelo de dados

```sql
CREATE TABLE person (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    type            text NOT NULL CHECK (type IN ('PF','PJ')),
    legal_name      text NOT NULL,                 -- razão social ou nome completo
    trade_name      text,                          -- nome fantasia (PJ) ou apelido (PF)
    cpf             char(11),
    cnpj            char(14),
    rg              text,
    ie              text,                          -- inscrição estadual (PJ)
    im              text,                          -- inscrição municipal (PJ)
    ie_exempt       boolean NOT NULL DEFAULT false,
    birth_date      date,                          -- PF
    foundation_date date,                          -- PJ
    is_active       boolean NOT NULL DEFAULT true,
    deactivated_at  timestamptz,
    anonymized      boolean NOT NULL DEFAULT false,
    anonymized_at   timestamptz,
    notes           text,
    segment_id      uuid REFERENCES catalog_segment(id),
    region_id       uuid REFERENCES catalog_region(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_person_document_xor
        CHECK ((type='PF' AND cpf IS NOT NULL AND cnpj IS NULL)
            OR (type='PJ' AND cnpj IS NOT NULL AND cpf IS NULL))
);
CREATE UNIQUE INDEX uq_person_cpf  ON person(cpf)  WHERE cpf  IS NOT NULL AND anonymized = false;
CREATE UNIQUE INDEX uq_person_cnpj ON person(cnpj) WHERE cnpj IS NOT NULL AND anonymized = false;
CREATE INDEX idx_person_name ON person(LOWER(legal_name)) WHERE is_active = true;

CREATE TABLE person_modality (
    person_id    uuid NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    modality     text NOT NULL CHECK (modality IN ('CUSTOMER','SUPPLIER','CARRIER','SELLER','EMPLOYEE')),
    is_active    boolean NOT NULL DEFAULT true,
    activated_at timestamptz NOT NULL DEFAULT now(),
    deactivated_at timestamptz,
    PRIMARY KEY (person_id, modality)
);

CREATE TABLE person_address (
    id              uuid PRIMARY KEY,
    person_id       uuid NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    address_type_id uuid REFERENCES catalog_address_type(id),
    cep             char(8) NOT NULL,
    street          text NOT NULL,
    number          text,
    complement      text,
    neighborhood    text,
    city            text NOT NULL,
    state           char(2) NOT NULL,
    country         char(2) NOT NULL DEFAULT 'BR',
    reference       text,
    is_default      boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_person_default_address
    ON person_address(person_id) WHERE is_default = true;

CREATE TABLE person_contact (
    id              uuid PRIMARY KEY,
    person_id       uuid NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    contact_type_id uuid REFERENCES catalog_contact_type(id),
    value           text NOT NULL,           -- email ou telefone (formato canonizado)
    label           text,                    -- "comercial", "celular", etc
    is_primary      boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE branch (
    id              uuid PRIMARY KEY,
    version         bigint NOT NULL DEFAULT 0,
    code            text NOT NULL UNIQUE,    -- "001", "MTZ", etc
    name            text NOT NULL,
    is_main         boolean NOT NULL DEFAULT false,
    is_active       boolean NOT NULL DEFAULT true,
    address_id      uuid REFERENCES person_address(id),  -- opcional reuso de address
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_branch_main ON branch(is_main) WHERE is_main = true;

CREATE TABLE branch_fiscal (
    branch_id           uuid PRIMARY KEY REFERENCES branch(id) ON DELETE CASCADE,
    cnpj                char(14) NOT NULL UNIQUE,
    ie                  text,
    im                  text,
    ie_exempt           boolean NOT NULL DEFAULT false,
    tax_regime          text NOT NULL CHECK (tax_regime IN ('SIMPLES','SIMPLES_EXCEDENTE','PRESUMIDO','REAL','MEI')),
    csosn_default       char(3),               -- usado se Simples
    cst_icms_default    char(2),               -- usado se Presumido/Real
    cnae                text,
    cert_kv_secret_name text,                  -- nome do Secret no Azure Key Vault (contém PFX+senha)
    cert_thumbprint     text,
    cert_valid_from     timestamptz,
    cert_valid_until    timestamptz,
    cert_subject_cnpj   char(14),              -- CNPJ extraído do CN do certificado
    nfce_csc_id         text,                  -- ID CSC NFCe
    nfce_csc_token_enc  bytea,                 -- token cifrado
    nfe_environment     text NOT NULL DEFAULT 'PRODUCAO'
        CHECK (nfe_environment IN ('PRODUCAO','HOMOLOGACAO')),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_branch_fiscal_cert_expiry ON branch_fiscal(cert_valid_until)
    WHERE cert_valid_until IS NOT NULL;

CREATE TABLE employee (
    id              uuid PRIMARY KEY,
    person_id       uuid NOT NULL REFERENCES person(id),
    branch_id       uuid NOT NULL REFERENCES branch(id),
    job_title_id    uuid REFERENCES catalog_job_title(id),
    department_id   uuid REFERENCES catalog_department(id),
    salary          numeric(15,2),
    hired_at        date NOT NULL,
    terminated_at   date,
    is_active       boolean NOT NULL DEFAULT true,
    notes           text,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX uq_employee_active ON employee(person_id, branch_id)
    WHERE terminated_at IS NULL;

-- catálogos auxiliares (mesmo padrão)
CREATE TABLE catalog_segment            (id uuid PK, code text UNIQUE, name text NOT NULL, is_active boolean DEFAULT true);
CREATE TABLE catalog_region             (id uuid PK, code text UNIQUE, name text NOT NULL, is_active boolean DEFAULT true);
CREATE TABLE catalog_address_type       (id uuid PK, code text UNIQUE, name text NOT NULL, is_active boolean DEFAULT true);
CREATE TABLE catalog_contact_type       (id uuid PK, code text UNIQUE, name text NOT NULL, is_active boolean DEFAULT true);
CREATE TABLE catalog_job_title          (id uuid PK, code text UNIQUE, name text NOT NULL, is_active boolean DEFAULT true);
CREATE TABLE catalog_department         (id uuid PK, code text UNIQUE, name text NOT NULL, is_active boolean DEFAULT true);
CREATE TABLE catalog_measurement_unit   (id uuid PK, code text UNIQUE, name text NOT NULL, is_active boolean DEFAULT true);
CREATE TABLE catalog_payment_condition  (id uuid PK, code text UNIQUE, name text NOT NULL,
    installments_count int NOT NULL DEFAULT 1,
    days_first_installment int NOT NULL DEFAULT 0,
    days_between_installments int NOT NULL DEFAULT 30,
    is_active boolean DEFAULT true);
```

Seed inicial em `TenantBootstrapper`:

- **Modalidades**: já enumeradas em `person_modality.modality` CHECK.
- **Catálogos**: registros default (segmento "Geral", região "Brasil", address/contact type comuns, unidades de medida `UN`, `KG`, `L`, `M`, `M2`, `M3`, `PC`, `CX`).
- **Condições de pagamento**: `À VISTA` (1x), `30 DIAS` (1x em 30d), `2X 30/60`, `3X 30/60/90`.
- **Filial matriz**: criada com código `001` durante provisionamento, com `is_main=true`.

## Estrutura Java

```
dev.dimi.erp.cadastros
├── package-info.java     ← allowedDependencies={"identidade","shared"}
├── PersonId.java, BranchId.java, EmployeeId.java
├── eventos:
│   ├── PersonRegisteredEvent.java
│   ├── PersonAnonymizedEvent.java
│   ├── PersonModalityActivatedEvent.java
│   ├── BranchCreatedEvent.java
│   ├── BranchFiscalUpdatedEvent.java
│   ├── EmployeeHiredEvent.java
│   └── EmployeeTerminatedEvent.java
├── PersonQueryPort.java   ← interface exposta (find by id, summary, etc)
├── BranchQueryPort.java
└── internal/
    ├── domain/        Person, Address, Contact, Branch, BranchFiscal, Employee, catálogos
    ├── persistence/   *Repository
    ├── application/   RegisterPersonService, AnonymizePersonService,
    │                   CreateBranchService, UpdateBranchFiscalService,
    │                   HireEmployeeService, TerminateEmployeeService
    ├── web/           PersonController, BranchController, EmployeeController, CatalogController
    ├── adapter/       KeyVaultCertificateStore (Azure SDK), LocalFsCertificateStore (dev/test), CepLookupClient (ViaCEP)
    └── listener/      (sem listeners obrigatórios neste módulo)
```

## Casos de uso e endpoints

### Pessoas

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/cadastros/persons` | `pessoa.consultar` |
| `POST` | `/api/v1/cadastros/persons` | `pessoa.criar` |
| `GET`  | `/api/v1/cadastros/persons/{id}` | `pessoa.consultar` |
| `PATCH`| `/api/v1/cadastros/persons/{id}` | `pessoa.editar` |
| `DELETE`| `/api/v1/cadastros/persons/{id}` | `pessoa.deletar` |
| `POST` | `/api/v1/cadastros/persons/{id}/modalities/{modality}/activate` | `pessoa.editar` |
| `POST` | `/api/v1/cadastros/persons/{id}/modalities/{modality}/deactivate` | `pessoa.editar` |
| `POST` | `/api/v1/cadastros/persons/{id}/addresses` | `pessoa.editar` |
| `PATCH`| `/api/v1/cadastros/persons/{id}/addresses/{addressId}` | `pessoa.editar` |
| `POST` | `/api/v1/cadastros/persons/{id}/contacts` | `pessoa.editar` |
| `POST` | `/api/v1/cadastros/persons/{id}/anonymize` | `pessoa.anonimizar` + step-up |
| `GET`  | `/api/v1/cadastros/persons/{id}/personal-data-export` | `pessoa.consultar` |

### Filiais

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/cadastros/branches` | `filial.consultar` |
| `POST` | `/api/v1/cadastros/branches` | `filial.criar` + step-up |
| `GET`  | `/api/v1/cadastros/branches/{id}` | `filial.consultar` |
| `PATCH`| `/api/v1/cadastros/branches/{id}` | `filial.editar` |
| `PUT`  | `/api/v1/cadastros/branches/{id}/fiscal` | `filial.fiscal.editar` + step-up |
| `PUT`  | `/api/v1/cadastros/branches/{id}/certificate` | `filial.fiscal.editar` + step-up (multipart PFX + senha) |
| `DELETE` | `/api/v1/cadastros/branches/{id}/certificate` | `filial.fiscal.editar` + step-up |

### Funcionários

| Método | Rota | Permissão |
|---|---|---|
| `GET`  | `/api/v1/cadastros/employees` | `funcionario.consultar` |
| `POST` | `/api/v1/cadastros/employees` | `funcionario.contratar` |
| `GET`  | `/api/v1/cadastros/employees/{id}` | `funcionario.consultar` |
| `PATCH`| `/api/v1/cadastros/employees/{id}` | `funcionario.editar` |
| `POST` | `/api/v1/cadastros/employees/{id}/terminate` | `funcionario.desligar` + step-up |

### Catálogos

Padrão `/api/v1/cadastros/catalogs/<recurso>` para cada catálogo:

| Recurso | Permissão CRUD |
|---|---|
| `segments` | `catalogo.editar` |
| `regions` | `catalogo.editar` |
| `address-types` | `catalogo.editar` |
| `contact-types` | `catalogo.editar` |
| `job-titles` | `catalogo.editar` |
| `departments` | `catalogo.editar` |
| `measurement-units` | `catalogo.editar` |
| `payment-conditions` | `catalogo.editar` |
| `modalities` | (consulta) `catalogo.consultar` — modalidades são enum, não CRUD |

## Eventos

```java
public record PersonRegisteredEvent(PersonId personId, TenantId tenantId, PersonType type, String legalName, Instant occurredAt) {}
public record PersonModalityActivatedEvent(PersonId personId, TenantId tenantId, Modality modality, Instant occurredAt) {}
public record PersonAnonymizedEvent(PersonId personId, TenantId tenantId, UserId by, Instant occurredAt) {}
public record BranchCreatedEvent(BranchId branchId, TenantId tenantId, String code, boolean isMain, Instant occurredAt) {}
public record BranchFiscalUpdatedEvent(BranchId branchId, TenantId tenantId, TaxRegime regime, Instant occurredAt) {}
public record CertificateUploadedEvent(BranchId branchId, TenantId tenantId, String thumbprint, Instant validUntil, Instant occurredAt) {}
public record CertificateExpiringEvent(BranchId branchId, TenantId tenantId, String thumbprint, Instant validUntil, int daysRemaining, Instant occurredAt) {}
public record EmployeeHiredEvent(EmployeeId employeeId, PersonId personId, BranchId branchId, LocalDate hiredAt, Instant occurredAt) {}
public record EmployeeTerminatedEvent(EmployeeId employeeId, PersonId personId, LocalDate terminatedAt, Instant occurredAt) {}
```

Consumidores:

- **`auditoria`**: tudo.
- **`fiscal`**: `BranchFiscalUpdatedEvent`, `CertificateUploadedEvent`, `CertificateExpiringEvent` (limpa cache de certificado, valida nova validade, notifica OWNER).
- **`identidade`**: `EmployeeTerminatedEvent` opcional para desativar `user_account` vinculado.

## Regras de negócio

- **RN-CAD-001**: CPF e CNPJ validados com DV. Consumidor final (`00000000000`/`00000000000000`) permite duplicata (apenas modalidade `CUSTOMER`, sem dados pessoais reais).
- **RN-CAD-002**: Pessoa é **soft delete** (`is_active=false`). Hard delete proibido pelas referências fiscais.
- **RN-CAD-003**: Tipo de pessoa (PF/PJ) é **imutável** após criação.
- **RN-CAD-004**: Endereço padrão único por pessoa, garantido por índice parcial. Definir um novo como padrão automaticamente desmarca o anterior.
- **RN-CAD-005**: Modalidades coexistem na mesma pessoa. Desativar uma modalidade não afeta as outras nem deleta a pessoa.
- **RN-CAD-006**: Filial sem `branch_fiscal` registrado **não pode emitir notas**. Módulo `fiscal` valida no momento da emissão.
- **RN-CAD-007**: Certificado A1 é upload `multipart/form-data` (PFX + senha). Backend: valida formato PKCS#12 + senha; extrai thumbprint, valid_from, valid_until, CNPJ; serializa `{pfx_b64, password}` como JSON e grava como **Secret no Azure Key Vault** (encryption-at-rest nativa, HSM-backed). Em `branch_fiscal` ficam só os metadados + `cert_kv_secret_name`. Substituição cria nova versão do Secret (Key Vault mantém versões antigas por configuração de retention policy).
- **RN-CAD-008**: Substituir certificado cria nova versão no Key Vault (não deleta). Versões antigas ficam acessíveis para validação retroativa de NFe por 90 dias via Key Vault retention; depois purga automática.
- **RN-CAD-009**: Job diário detecta certificados com `valid_until` em 30/15/7 dias e emite evento `CertificateExpiringEvent` → notificação email para OWNER.
- **RN-CAD-010**: Funcionário ativo é único por `(person, branch)` — pessoa pode ser funcionário em duas filiais, mas não duas vezes na mesma.
- **RN-CAD-011**: Anonimização (LGPD Art. 18 IV): substitui `legal_name` por hash determinístico (`anon_<base32>`), `cpf/cnpj/rg/ie/im` por nulos, `birth_date/foundation_date` por nulo, mantém ID + `created_at` + modalidades (para integridade fiscal). Operação requer step-up + permission `pessoa.anonimizar`. Audit log eterno (não purga).
- **RN-CAD-012**: Anonimizada não pode ser editada nem ter modalidade reativada.
- **RN-CAD-013**: Catálogos têm seed inicial; tenant pode adicionar mas registros system (com `is_system=true` em coluna a adicionar quando aplicável) não podem ser deletados.
- **RN-CAD-014**: Apenas 1 filial pode ter `is_main=true` por tenant (garantido por índice parcial).
- **RN-CAD-015**: Endereço CEP é lookup via **ViaCEP** (cliente HTTP em `adapter`) para preencher rua/bairro/cidade/UF — opcional, validado mas não obrigatório.
- **RN-CAD-016**: Export de dados pessoais (LGPD Art. 18 V) retorna JSON consolidado com person + addresses + contacts + modalidades + referências em outros módulos (vendas, MVF, OS, contratos) via `personal-data-export` que consulta cada módulo via Query Port.

## Integrações

- **Módulo `identidade`**: `user_account.person_id` referência soft (opcional).
- **Módulo `produto/estoque/movimentação/financeiro/fiscal/OS/contratos`**: consomem `PersonQueryPort` e `BranchQueryPort` para obter dados básicos sem importar entidades.
- **Azure Key Vault**: armazenamento unificado de certificados A1 como Secrets (PFX+senha em JSON). Sem Blob Storage no v1.
- **ViaCEP**: lookup de CEP para preenchimento automático de endereço.

## Critérios de aceitação

- **Dado** uma PJ válida com CNPJ e modalidade SUPPLIER, **quando** registrada, **então** evento `PersonRegisteredEvent` + `PersonModalityActivatedEvent` são publicados e a pessoa aparece em `/persons?modality=SUPPLIER`.
- **Dado** um CPF inválido (DV errado), **quando** tentativa de cadastro, **então** retorna `400` com `code=person.invalid-cpf`.
- **Dado** um CPF já cadastrado, **quando** novo cadastro tenta usá-lo, **então** retorna `409` com `code=person.duplicate-cpf`.
- **Dado** uma filial sem `branch_fiscal`, **quando** módulo fiscal tenta emitir NFe, **então** retorna `422` com `code=fiscal.branch-not-fiscally-configured`.
- **Dado** um certificado A1 válido, **quando** upload, **então** PFX+senha salvos como Secret no Key Vault, `cert_kv_secret_name` + thumbprint + `valid_from/until` + `subject_cnpj` registrados, evento `CertificateUploadedEvent` publicado.
- **Dado** um PFX com senha incorreta, **quando** upload, **então** retorna `400` com `code=cadastros.invalid-pfx-password`.
- **Dado** um certificado A1 com `valid_until` em 30/15/7 dias, **quando** job diário roda, **então** `CertificateExpiringEvent` é publicado uma vez por marco.
- **Dado** uma pessoa anonimizada, **quando** consulta de dados pessoais, **então** retorna 200 com payload mascarado e marcação `anonymized=true`.
- **Dado** uma pessoa anonimizada, **quando** tentativa de edição, **então** retorna `409` com `code=person.anonymized-immutable`.

## Não-funcionais

- **Latência GET person**: p95 < 100ms.
- **Latência POST person**: p95 < 200ms (sem efeitos cross-module).
- **Upload de certificado**: < 3s para PFX típico (~5KB).
- **Anonimização**: < 500ms (single update + evento async).

## Roadmap

- **v1.1**: validação online de CPF/CNPJ na Receita (Webservice gov); SintegraWS para validação de IE; merge de pessoas duplicadas (deduplicação com aprovação).
- **v1.2**: parentesco e relacionamento entre pessoas (sócios, dependentes, referências comerciais).
- **v2**: integração com sistemas externos via API (CRM, lojistas) para sincronização de pessoas.
