-- Seed do catálogo global de permissões.
-- ON CONFLICT DO NOTHING torna a migration idempotente para reaplicação acidental.
-- Novas permissions entram em migrations seguintes (V4__seed_permissions_v2.sql etc).

INSERT INTO shared.permission (code, display_name, description, category, is_sensitive) VALUES
    -- ===== Identidade & Acesso =====
    ('usuario.consultar',          'Consultar usuários',                'Listar e visualizar dados de usuários.', 'identidade', false),
    ('usuario.criar',              'Criar usuário',                     'Criar novos usuários no tenant.', 'identidade', true),
    ('usuario.editar',             'Editar usuário',                    'Editar dados cadastrais e ativar/desativar usuários.', 'identidade', true),
    ('usuario.permissoes.alterar', 'Alterar permissões de usuário',     'Atribuir e revogar roles.', 'identidade', true),
    ('role.consultar',             'Consultar roles',                   'Listar roles e permissões do tenant.', 'identidade', false),
    ('role.editar',                'Editar role',                       'Criar/editar/remover roles customizadas.', 'identidade', true),
    ('senha.resetar.outro-usuario','Resetar senha de outro usuário',   'Forçar reset de senha de outro usuário.', 'identidade', true),
    ('mfa.resetar',                'Resetar MFA de outro usuário',      'Remover configuração MFA de outro usuário.', 'identidade', true),

    -- ===== Auditoria =====
    ('audit.consultar',            'Consultar audit log',               'Consultar eventos de auditoria operacional.', 'auditoria', false),
    ('audit.sensitive.consultar',  'Consultar audit sensível',          'Acessar eventos de severidade CRITICAL.', 'auditoria', true),
    ('audit.exportar',             'Exportar audit log',                'Gerar CSV/JSON com payload completo.', 'auditoria', true),
    ('audit.administrar',          'Administrar audit',                 'Alterar políticas de retenção.', 'auditoria', true),
    ('lgpd.consultar',             'Consultar eventos LGPD',            'Acessar eventos de anonimização/portabilidade.', 'auditoria', false),

    -- ===== Cadastros (slice 3) =====
    ('pessoa.consultar',           'Consultar pessoas',                 'Listar e visualizar pessoas.', 'cadastros', false),
    ('pessoa.criar',               'Criar pessoa',                      'Registrar nova pessoa (PF/PJ).', 'cadastros', false),
    ('pessoa.editar',              'Editar pessoa',                     'Alterar dados de pessoa existente.', 'cadastros', false),
    ('pessoa.deletar',             'Desativar pessoa',                  'Soft-delete de pessoa.', 'cadastros', false),
    ('pessoa.anonimizar',          'Anonimizar pessoa (LGPD)',         'Anonimizar dados pessoais conforme LGPD.', 'cadastros', true),
    ('filial.consultar',           'Consultar filiais',                 'Listar e visualizar filiais.', 'cadastros', false),
    ('filial.criar',               'Criar filial',                      'Criar nova filial operacional.', 'cadastros', true),
    ('filial.editar',              'Editar filial',                     'Alterar dados de filial.', 'cadastros', false),
    ('filial.fiscal.editar',       'Editar dados fiscais da filial',    'Alterar CNPJ, regime, certificado A1.', 'cadastros', true),
    ('funcionario.consultar',      'Consultar funcionários',            'Listar funcionários.', 'cadastros', false),
    ('funcionario.contratar',      'Contratar funcionário',             'Registrar contratação.', 'cadastros', false),
    ('funcionario.editar',         'Editar funcionário',                'Alterar dados de funcionário ativo.', 'cadastros', false),
    ('funcionario.desligar',       'Desligar funcionário',              'Registrar desligamento.', 'cadastros', true),
    ('catalogo.consultar',         'Consultar catálogos',               'Ler segmento, região, cargo, depto, etc.', 'cadastros', false),
    ('catalogo.editar',            'Editar catálogos',                  'CRUD de catálogos auxiliares.', 'cadastros', false),

    -- ===== Produto e Estoque (slice 4) =====
    ('produto.consultar',          'Consultar produtos',                'Listar e visualizar produtos.', 'produto', false),
    ('produto.criar',              'Criar produto',                     'Cadastrar produto, variedade, embalagem.', 'produto', false),
    ('produto.editar',             'Editar produto',                    'Alterar dados de produto.', 'produto', false),
    ('produto.preco.consultar',    'Consultar tabela de preços',        'Visualizar tabelas de preço.', 'produto', false),
    ('produto.preco.alterar',      'Alterar tabela de preços',          'Criar/editar tabelas de preço.', 'produto', true),
    ('estoque.consultar',          'Consultar estoque',                 'Visualizar saldos e movimentos.', 'estoque', false),
    ('estoque.editar',             'Configurar estoque',                'Criar/editar locais e lotes.', 'estoque', false),
    ('estoque.ajustar',            'Ajustar estoque manualmente',       'Aplicar correção de inventário.', 'estoque', true),
    ('estoque.transferir',         'Transferir estoque',                'Mover entre locais/filiais.', 'estoque', false),

    -- ===== Movimentação (slice 5) =====
    ('pedido.consultar',           'Consultar pedidos',                 'Listar pedidos de venda.', 'movimentacao', false),
    ('pedido.criar',               'Criar pedido',                      'Registrar pedido de venda.', 'movimentacao', false),
    ('pedido.editar',              'Editar pedido',                     'Alterar pedido em rascunho.', 'movimentacao', false),
    ('pedido.aprovar',             'Aprovar pedido',                    'Aprovar/rejeitar pedido pendente.', 'movimentacao', false),
    ('venda.consultar',            'Consultar vendas',                  'Listar vendas.', 'movimentacao', false),
    ('venda.criar',                'Criar venda',                       'Iniciar venda nova.', 'movimentacao', false),
    ('venda.fechar',               'Fechar venda',                      'Confirmar venda e baixar estoque.', 'movimentacao', false),
    ('venda.cancelar',             'Cancelar venda',                    'Cancelar venda fechada (Saga).', 'movimentacao', true),
    ('venda.credito.exceder',      'Exceder limite de crédito',         'Override de limite de crédito.', 'movimentacao', true),
    ('venda.desconto.exceder',     'Exceder desconto máximo',           'Override de desconto por item.', 'movimentacao', true),
    ('devolucao.consultar',        'Consultar devoluções',              'Listar devoluções de venda.', 'movimentacao', false),
    ('devolucao.criar',            'Criar devolução',                   'Registrar devolução de venda.', 'movimentacao', false),
    ('devolucao.fechar',           'Fechar devolução',                  'Confirmar devolução e estornar.', 'movimentacao', false),
    ('transferencia.consultar',    'Consultar transferências',          'Listar transferências de estoque.', 'movimentacao', false),
    ('transferencia.criar',        'Criar transferência',               'Iniciar transferência de estoque.', 'movimentacao', false),
    ('transferencia.fechar',       'Fechar transferência',              'Confirmar transferência.', 'movimentacao', false),
    ('saida-avulsa.consultar',     'Consultar saídas avulsas',          'Listar amostras, brindes, perdas.', 'movimentacao', false),
    ('saida-avulsa.criar',         'Registrar saída avulsa',            'Amostra, brinde, garantia, perda.', 'movimentacao', true),

    -- ===== Compras (slice 6) =====
    ('compra.consultar',           'Consultar compras',                 'Listar solicitações, cotações, pedidos.', 'compras', false),
    ('compra.solicitar',           'Solicitar compra',                  'Criar solicitação de compra.', 'compras', false),
    ('compra.solicitar.aprovacao-direta', 'Aprovar SC sem revisão',     'Aprovar a própria SC sem aprovador.', 'compras', false),
    ('compra.aprovar',             'Aprovar SC',                        'Aprovar/rejeitar solicitações.', 'compras', false),
    ('compra.cotar',               'Conduzir cotação',                  'Criar cotação e registrar respostas.', 'compras', false),
    ('compra.pedir',               'Emitir pedido de compra',           'Gerar PC a partir de cotação ou direto.', 'compras', false),
    ('compra.pedir.sem-cotacao',   'PC sem cotação prévia',             'Override pra compra rápida.', 'compras', false),
    ('compra.receber',             'Receber mercadoria',                'Registrar recebimento (INBOUND).', 'compras', false),
    ('compra.receber.excesso',     'Receber acima do pedido',           'Permite receber > qty ordered.', 'compras', false),
    ('compra.cancelar',            'Cancelar PC',                       'Cancelar pedido de compra.', 'compras', true),
    ('compra.devolver-fornecedor', 'Devolver a fornecedor',             'Registrar devolução a fornecedor.', 'compras', true),

    -- ===== Financeiro (slice 7) =====
    ('mvf.consultar',              'Consultar MVF',                     'Listar movimentações financeiras.', 'financeiro', false),
    ('mvf.criar',                  'Criar MVF',                         'Criar conta a pagar/receber manual.', 'financeiro', false),
    ('mvf.editar',                 'Editar MVF',                        'Alterar vencimento e rateio.', 'financeiro', false),
    ('mvf.cancelar',               'Cancelar MVF',                      'Cancelar MVF sem pagamentos.', 'financeiro', true),
    ('mvf.renegociar',             'Renegociar MVF',                    'Renegociar saldo aberto.', 'financeiro', true),
    ('pagamento.consultar',        'Consultar pagamentos',              'Listar pagamentos processados.', 'financeiro', false),
    ('pagamento.registrar',        'Registrar pagamento',               'Aplicar pagamento em parcelas.', 'financeiro', false),
    ('pagamento.estornar',         'Estornar pagamento',                'Reverter pagamento processado.', 'financeiro', true),
    ('plano-contas.consultar',     'Consultar plano de contas',         'Visualizar plano gerencial.', 'financeiro', false),
    ('plano-contas.editar',        'Editar plano de contas',            'Criar/editar contas do plano.', 'financeiro', true),
    ('centro-custo.consultar',     'Consultar centros de custo',        'Visualizar centros.', 'financeiro', false),
    ('centro-custo.editar',        'Editar centros de custo',           'Criar/editar centros.', 'financeiro', false),
    ('conta-bancaria.consultar',   'Consultar contas bancárias',        'Visualizar contas.', 'financeiro', false),
    ('conta-bancaria.editar',      'Editar contas bancárias',           'CRUD de contas bancárias.', 'financeiro', true),
    ('periodo.consultar',          'Consultar períodos congelados',     'Visualizar congelamentos.', 'financeiro', false),
    ('periodo.congelar',           'Congelar período contábil',         'Travar período fechado.', 'financeiro', true),
    ('periodo.descongelar',        'Descongelar período',               'Reabrir período fechado.', 'financeiro', true),
    ('cnab.consultar',             'Consultar lotes CNAB',              'Visualizar remessas/retornos CNAB.', 'financeiro', false),
    ('cnab.gerar',                 'Gerar remessa CNAB',                'Gerar arquivo .REM.', 'financeiro', false),
    ('cnab.processar',             'Processar retorno CNAB',            'Importar arquivo .RET.', 'financeiro', false),
    ('conciliacao.consultar',      'Consultar conciliação bancária',    'Visualizar matches.', 'financeiro', false),
    ('conciliacao.executar',       'Executar conciliação',              'Match manual de extrato.', 'financeiro', false),
    ('relatorio.financeiro',       'Acessar relatórios financeiros',    'DRE, fluxo de caixa, aging.', 'financeiro', false),

    -- ===== Fiscal (slice 8) =====
    ('nfe.consultar',              'Consultar NFes',                    'Listar documentos fiscais.', 'fiscal', false),
    ('nfe.emitir',                 'Emitir NFe/NFCe/NFSe',              'Autorizar documento fiscal.', 'fiscal', true),
    ('nfe.cancelar',               'Cancelar NFe',                      'Cancelar documento autorizado.', 'fiscal', true),
    ('nfe.cce',                    'Emitir CCe',                        'Carta de correção.', 'fiscal', true),
    ('nfe.inutilizar',             'Inutilizar numeração',              'Inutilizar faixa de números.', 'fiscal', true),
    ('nfe.contingencia',           'Operar contingência',               'Forçar entrada/saída de contingência.', 'fiscal', true),
    ('dfe.consultar',              'Consultar DFe',                     'Listar NFes recebidas.', 'fiscal', false),
    ('dfe.sincronizar',            'Sincronizar DFe',                   'Forçar sync com SEFAZ.', 'fiscal', false),
    ('dfe.manifestar',             'Manifestar DFe',                    'Ciência/confirmação/desconhecimento.', 'fiscal', false),
    ('fiscal.relatorios',          'Gerar SPED',                        'SPED Fiscal e Contribuições.', 'fiscal', false),

    -- ===== Ordem de Serviço (slice 9) =====
    ('os.consultar',               'Consultar OS',                      'Listar ordens de serviço.', 'os', false),
    ('os.criar',                   'Criar OS',                          'Abrir ordem de serviço.', 'os', false),
    ('os.editar',                  'Editar OS',                         'Alterar OS não-faturada.', 'os', false),
    ('os.apontar-mao-obra',        'Apontar mão-de-obra',               'Registrar horas e técnicos.', 'os', false),
    ('os.faturar',                 'Faturar OS',                        'Emitir NFe+NFSe da OS.', 'os', false),
    ('os.fechar',                  'Fechar OS',                         'Encerrar OS faturada.', 'os', false),
    ('os.cancelar',                'Cancelar OS',                       'Cancelar OS (Saga).', 'os', true),
    ('os.configurar-workflow',     'Configurar workflow de OS',         'CRUD de status e transições.', 'os', true),

    -- ===== Contratos (slice 10) =====
    ('contrato.consultar',         'Consultar contratos',               'Listar contratos.', 'contratos', false),
    ('contrato.criar',             'Criar contrato',                    'Registrar contrato em rascunho.', 'contratos', false),
    ('contrato.editar',            'Editar contrato',                   'Alterar contrato em DRAFT.', 'contratos', false),
    ('contrato.ativar',            'Ativar contrato',                   'Iniciar vigência de contrato.', 'contratos', true),
    ('contrato.suspender',         'Suspender contrato',                'Pausar contrato ativo.', 'contratos', true),
    ('contrato.encerrar',          'Encerrar contrato',                 'Encerrar antecipadamente ou no termo.', 'contratos', true),
    ('contrato.aditivar',          'Aditivar contrato',                 'Aplicar aditivo (prazo, valor, escopo).', 'contratos', true),
    ('contrato.reajustar',         'Reajustar contrato',                'Aplicar reajuste por índice.', 'contratos', true),
    ('contrato.faturar',           'Faturar ciclo de contrato',         'Regenerar ciclo manualmente.', 'contratos', false),

    -- ===== Plataforma (super-admin) =====
    ('platform.tenant.criar',      'Provisionar tenant',                'Criar novo tenant na plataforma.', 'platform', true),
    ('platform.tenant.suspender',  'Suspender tenant',                  'Bloquear acesso de um tenant.', 'platform', true),
    ('platform.tenant.encerrar',   'Encerrar tenant',                   'Terminar tenant (LGPD).', 'platform', true),
    ('platform.observability',     'Administrar observabilidade',       'Alterar log levels em runtime.', 'platform', true)
ON CONFLICT (code) DO NOTHING;

-- Sanity check: garantir que pelo menos OWNER seed (V1 do tenant) tem fonte
DO $$
DECLARE
    perm_count integer;
BEGIN
    SELECT COUNT(*) INTO perm_count FROM shared.permission;
    IF perm_count < 50 THEN
        RAISE EXCEPTION 'Seed de permissions falhou: apenas % registros', perm_count;
    END IF;
END $$;
