-- Permissão para administrar módulos opcionais do tenant.
-- Concedida às roles administrativas pelo catálogo Java e pelo backfill do tenant.
--
-- tenant.modulo.configurar: ativa/desativa módulos opcionais do tenant.
-- Sensível porque toggling fiscal/financeiro afeta compliance e pode esconder dados.

INSERT INTO shared.permission (code, display_name, description, category, is_sensitive) VALUES
    ('tenant.modulo.configurar', 'Configurar módulos do tenant',
     'Ativar ou desativar módulos opcionais do tenant.',
     'identidade', true)
ON CONFLICT (code) DO NOTHING;
