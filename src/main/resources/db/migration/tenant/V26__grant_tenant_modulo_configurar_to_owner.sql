-- Slice 11.5 backfill: garante que a role OWNER do tenant tenha a permission
-- tenant.modulo.configurar.
--
-- Tenants novos recebem essa permissão via TenantProvisioner + SystemRoles,
-- mas tenants provisionados antes do V10 global precisam deste catch-up.
--
-- Por design só o OWNER configura módulos (MANAGER e outros NÃO recebem).
--
-- Idempotente: ON CONFLICT DO NOTHING evita duplicar quando o seed Java já
-- gravou. INSERT-SELECT retorna zero linhas se a role OWNER ainda não existe
-- (caso o V26 rode antes do seed Java em um tenant novo, o que é OK — o
-- seed Java cuida).

INSERT INTO role_permission (role_id, permission_code)
SELECT id, 'tenant.modulo.configurar'
FROM role
WHERE code = 'OWNER'
ON CONFLICT (role_id, permission_code) DO NOTHING;
