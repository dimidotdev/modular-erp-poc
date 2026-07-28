-- Migração aplicada POR TENANT (TenantProvisioner + Flyway tenant locations).
-- Cria o esqueleto completo do módulo cadastros: catálogos auxiliares, pessoa
-- polimórfica (PF/PJ) com modalidades/endereços/contatos, filial com bloco
-- fiscal 1:1, e vínculo de funcionário.
--
-- Slice 3.1 foundation: schema completo, sem services/controllers ainda.
-- Colunas relacionadas a certificado A1 e cifragem (slice 3.5) já existem
-- como nullable para evitar migration churn futura.
--
-- Doc: docs/domain/11-cadastros.md.

-- =====================================================================
-- Catálogos auxiliares
-- =====================================================================
-- Padrão comum: id UUID, code UNIQUE, name, is_system (seed protegido),
-- is_active (soft delete pelo tenant), timestamps.

CREATE TABLE catalog_segment (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    is_system   boolean NOT NULL DEFAULT false,
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE catalog_region (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    is_system   boolean NOT NULL DEFAULT false,
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE catalog_address_type (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    is_system   boolean NOT NULL DEFAULT false,
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE catalog_contact_type (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    is_system   boolean NOT NULL DEFAULT false,
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE catalog_job_title (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    is_system   boolean NOT NULL DEFAULT false,
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE catalog_department (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    is_system   boolean NOT NULL DEFAULT false,
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE catalog_measurement_unit (
    id          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code        text NOT NULL UNIQUE,
    name        text NOT NULL,
    is_system   boolean NOT NULL DEFAULT false,
    is_active   boolean NOT NULL DEFAULT true,
    created_at  timestamptz NOT NULL DEFAULT now(),
    updated_at  timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE catalog_payment_condition (
    id                          uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    code                        text NOT NULL UNIQUE,
    name                        text NOT NULL,
    installments_count          int  NOT NULL DEFAULT 1,
    days_first_installment      int  NOT NULL DEFAULT 0,
    days_between_installments   int  NOT NULL DEFAULT 30,
    is_system                   boolean NOT NULL DEFAULT false,
    is_active                   boolean NOT NULL DEFAULT true,
    created_at                  timestamptz NOT NULL DEFAULT now(),
    updated_at                  timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_payment_cond_installments_positive
        CHECK (installments_count >= 1),
    CONSTRAINT ck_payment_cond_days_first_non_negative
        CHECK (days_first_installment >= 0),
    CONSTRAINT ck_payment_cond_days_between_positive
        CHECK (days_between_installments >= 1)
);

-- =====================================================================
-- person — polimórfica PF/PJ com múltiplas modalidades
-- =====================================================================
CREATE TABLE person (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint NOT NULL DEFAULT 0,
    type            text NOT NULL CHECK (type IN ('PF','PJ')),
    legal_name      text NOT NULL,
    trade_name      text,
    cpf             char(11),
    cnpj            char(14),
    rg              text,
    ie              text,
    im              text,
    ie_exempt       boolean NOT NULL DEFAULT false,
    birth_date      date,
    foundation_date date,
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
            OR (type='PJ' AND cnpj IS NOT NULL AND cpf IS NULL)),
    CONSTRAINT ck_person_anonymized_consistent
        CHECK ((anonymized = false AND anonymized_at IS NULL)
            OR (anonymized = true  AND anonymized_at IS NOT NULL))
);
-- CPF/CNPJ únicos somente para registros não anonimizados.
CREATE UNIQUE INDEX uq_person_cpf  ON person(cpf)
    WHERE cpf  IS NOT NULL AND anonymized = false;
CREATE UNIQUE INDEX uq_person_cnpj ON person(cnpj)
    WHERE cnpj IS NOT NULL AND anonymized = false;
CREATE INDEX idx_person_legal_name_lower ON person(LOWER(legal_name))
    WHERE is_active = true;

COMMENT ON TABLE person IS
    'Pessoas físicas e jurídicas. Soft delete (is_active). Anonimização LGPD (anonymized) torna documento NULL e remove dados pessoais identificáveis.';

CREATE TABLE person_modality (
    person_id       uuid NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    modality        text NOT NULL CHECK (modality IN ('CUSTOMER','SUPPLIER','CARRIER','SELLER','EMPLOYEE')),
    is_active       boolean NOT NULL DEFAULT true,
    activated_at    timestamptz NOT NULL DEFAULT now(),
    deactivated_at  timestamptz,
    PRIMARY KEY (person_id, modality)
);
CREATE INDEX idx_person_modality_active ON person_modality(modality)
    WHERE is_active = true;

COMMENT ON TABLE person_modality IS
    'Modalidades que a pessoa desempenha. Mesma pessoa pode ser CUSTOMER + SUPPLIER simultaneamente.';

CREATE TABLE person_address (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
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
CREATE UNIQUE INDEX uq_person_default_address ON person_address(person_id)
    WHERE is_default = true;
CREATE INDEX idx_person_address_person ON person_address(person_id);

CREATE TABLE person_contact (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    person_id       uuid NOT NULL REFERENCES person(id) ON DELETE CASCADE,
    contact_type_id uuid REFERENCES catalog_contact_type(id),
    value           text NOT NULL,
    label           text,
    is_primary      boolean NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_person_contact_person ON person_contact(person_id);

-- =====================================================================
-- branch + branch_fiscal
-- =====================================================================
CREATE TABLE branch (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    version         bigint NOT NULL DEFAULT 0,
    code            text NOT NULL UNIQUE,
    name            text NOT NULL,
    is_main         boolean NOT NULL DEFAULT false,
    is_active       boolean NOT NULL DEFAULT true,
    address_id      uuid REFERENCES person_address(id),
    created_at      timestamptz NOT NULL DEFAULT now(),
    updated_at      timestamptz NOT NULL DEFAULT now()
);
-- RN-CAD-014: uma única matriz por tenant.
CREATE UNIQUE INDEX uq_branch_main ON branch(is_main) WHERE is_main = true;

COMMENT ON TABLE branch IS
    'Unidade operacional do tenant. is_main marca a matriz (única por índice parcial).';

-- branch_fiscal: dados fiscais separados em 1:1 para isolar campos sensíveis
-- (certificado A1 + senha cifrada) e simplificar permissionamento.
-- Slice 3.5 (ou na fronteira com fiscal) ativa os campos de certificado.
CREATE TABLE branch_fiscal (
    branch_id           uuid PRIMARY KEY REFERENCES branch(id) ON DELETE CASCADE,
    cnpj                char(14) NOT NULL UNIQUE,
    ie                  text,
    im                  text,
    ie_exempt           boolean NOT NULL DEFAULT false,
    tax_regime          text NOT NULL CHECK (tax_regime IN ('SIMPLES','SIMPLES_EXCEDENTE','PRESUMIDO','REAL','MEI')),
    csosn_default       char(3),
    cst_icms_default    char(2),
    cnae                text,
    cert_blob_path      text,
    cert_password_enc   bytea,
    cert_thumbprint     text,
    cert_valid_from     timestamptz,
    cert_valid_until    timestamptz,
    nfce_csc_id         text,
    nfce_csc_token_enc  bytea,
    nfe_environment     text NOT NULL DEFAULT 'PRODUCAO'
        CHECK (nfe_environment IN ('PRODUCAO','HOMOLOGACAO')),
    updated_at          timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_branch_fiscal_cert_expiry ON branch_fiscal(cert_valid_until)
    WHERE cert_valid_until IS NOT NULL;

-- =====================================================================
-- employee
-- =====================================================================
CREATE TABLE employee (
    id              uuid PRIMARY KEY DEFAULT gen_random_uuid(),
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
    updated_at      timestamptz NOT NULL DEFAULT now(),

    CONSTRAINT ck_employee_termination_after_hire
        CHECK (terminated_at IS NULL OR terminated_at >= hired_at)
);
-- RN-CAD-010: pessoa pode estar ativa em N filiais, mas não duas vezes na mesma.
CREATE UNIQUE INDEX uq_employee_active ON employee(person_id, branch_id)
    WHERE terminated_at IS NULL;
CREATE INDEX idx_employee_branch ON employee(branch_id) WHERE is_active = true;
