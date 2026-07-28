-- Migração aplicada POR TENANT.
-- tenant_module registra quais módulos opcionais estão habilitados no tenant.
-- Módulos essenciais (isCore=true no catálogo Java) permanecem habilitados e
-- não aceitam alteração pela API; os demais podem ser ativados ou desativados.

CREATE TABLE tenant_module (
    code         text PRIMARY KEY,             -- código curto: 'fiscal', 'frota', etc
    enabled      boolean NOT NULL DEFAULT true,
    enabled_at   timestamptz,                  -- nulo se nunca ativado
    enabled_by   uuid,                          -- usuário que ativou; null se default do provisioning
    disabled_at  timestamptz,                  -- nulo se enabled=true
    disabled_by  uuid,
    created_at   timestamptz NOT NULL DEFAULT now(),
    updated_at   timestamptz NOT NULL DEFAULT now()
);

-- Seed inicial: TenantProvisioner.provision() popula com TODOS os módulos do catálogo após
-- criação do tenant (lookup via Java ModuleCatalog.allModuleCodes()).
-- Tenants existentes ficam sem rows até o backfill via boot listener.
