-- Seed inicial dos catálogos + filial matriz aplicada por tenant.
-- Mantemos a seed em SQL (em vez de orquestrar via TenantProvisioner) para
-- evitar dependência circular identidade -> cadastros e porque o conteúdo
-- é estático.
--
-- Registros system (is_system=true) não podem ser deletados pelo tenant
-- (RN-CAD-013); novos catálogos custom podem ser adicionados livremente.

-- ---------------------------------------------------------------------
-- catalog_segment
-- ---------------------------------------------------------------------
INSERT INTO catalog_segment (code, name, is_system) VALUES
    ('GERAL',       'Geral',       true),
    ('VAREJO',      'Varejo',      true),
    ('ATACADO',     'Atacado',     true),
    ('INDUSTRIA',   'Indústria',   true),
    ('SERVICOS',    'Serviços',    true);

-- ---------------------------------------------------------------------
-- catalog_region
-- ---------------------------------------------------------------------
INSERT INTO catalog_region (code, name, is_system) VALUES
    ('BR', 'Brasil', true);

-- ---------------------------------------------------------------------
-- catalog_address_type
-- ---------------------------------------------------------------------
INSERT INTO catalog_address_type (code, name, is_system) VALUES
    ('COMERCIAL',   'Comercial',    true),
    ('RESIDENCIAL', 'Residencial',  true),
    ('ENTREGA',     'Entrega',      true),
    ('COBRANCA',    'Cobrança',     true);

-- ---------------------------------------------------------------------
-- catalog_contact_type
-- ---------------------------------------------------------------------
INSERT INTO catalog_contact_type (code, name, is_system) VALUES
    ('EMAIL',       'Email',        true),
    ('TELEFONE',    'Telefone',     true),
    ('CELULAR',     'Celular',      true),
    ('WHATSAPP',    'WhatsApp',     true);

-- ---------------------------------------------------------------------
-- catalog_job_title  (apenas exemplos comuns; tenant adiciona o resto)
-- ---------------------------------------------------------------------
INSERT INTO catalog_job_title (code, name, is_system) VALUES
    ('VENDEDOR',    'Vendedor',     true),
    ('CAIXA',       'Caixa',        true),
    ('GERENTE',     'Gerente',      true);

-- ---------------------------------------------------------------------
-- catalog_department
-- ---------------------------------------------------------------------
INSERT INTO catalog_department (code, name, is_system) VALUES
    ('COMERCIAL',   'Comercial',    true),
    ('FINANCEIRO',  'Financeiro',   true),
    ('FISCAL',      'Fiscal',       true),
    ('ESTOQUE',     'Estoque',      true),
    ('ADMINISTRATIVO', 'Administrativo', true);

-- ---------------------------------------------------------------------
-- catalog_measurement_unit
-- ---------------------------------------------------------------------
INSERT INTO catalog_measurement_unit (code, name, is_system) VALUES
    ('UN',  'Unidade',          true),
    ('KG',  'Quilograma',       true),
    ('G',   'Grama',            true),
    ('L',   'Litro',            true),
    ('ML',  'Mililitro',        true),
    ('M',   'Metro',            true),
    ('M2',  'Metro quadrado',   true),
    ('M3',  'Metro cúbico',     true),
    ('PC',  'Peça',             true),
    ('CX',  'Caixa',            true),
    ('PCT', 'Pacote',           true),
    ('DZ',  'Dúzia',            true);

-- ---------------------------------------------------------------------
-- catalog_payment_condition
-- ---------------------------------------------------------------------
INSERT INTO catalog_payment_condition
    (code, name, installments_count, days_first_installment, days_between_installments, is_system) VALUES
    ('AVISTA',      'À vista',                  1,  0,  30, true),
    ('30D',         '30 dias',                  1, 30,  30, true),
    ('2X_30_60',    '2x (30/60 dias)',          2, 30,  30, true),
    ('3X_30_60_90', '3x (30/60/90 dias)',       3, 30,  30, true);

-- ---------------------------------------------------------------------
-- Filial matriz
-- ---------------------------------------------------------------------
-- RN-CAD-014: uma única matriz por tenant (uq_branch_main).
-- branch_fiscal será preenchido em slice posterior (3.5) com certificado,
-- regime tributário, etc. Sem branch_fiscal, a matriz não pode emitir notas
-- (RN-CAD-006), mas existe como unidade operacional.
INSERT INTO branch (code, name, is_main, is_active) VALUES
    ('001', 'Matriz', true, true);
