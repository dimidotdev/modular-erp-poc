-- Slice 7.3: permissions para gerenciar tenant_setting (defaults financeiros
-- por tenant: chart_account/cost_center default consumidos por listeners de
-- SaleClosed/PurchaseReceived).
--
-- tenant.config.consultar: lê configuração (não sensível).
-- tenant.config.editar: altera configurações operacionais não classificadas
-- como sensíveis. Instalações podem exigir step-up por política adicional.

INSERT INTO shared.permission (code, display_name, description, category, is_sensitive) VALUES
    ('tenant.config.consultar', 'Consultar configuração do tenant',
     'Visualiza tenant_setting (contas default, centro de custo default).',
     'financeiro', false),
    ('tenant.config.editar', 'Editar configuração do tenant',
     'Atualiza tenant_setting; cadastra contas-default necessárias para listeners financeiros.',
     'financeiro', false)
ON CONFLICT (code) DO NOTHING;
