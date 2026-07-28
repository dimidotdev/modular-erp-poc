-- Slice 7.3 backfill: garante que a role OWNER do tenant tenha as permissions
-- tenant.config.consultar e tenant.config.editar.
--
-- Tenants novos recebem essa permissão via TenantProvisioner + SystemRoles,
-- mas tenants provisionados antes do V7 global precisam deste catch-up.
--
-- Idempotente: ON CONFLICT DO NOTHING evita duplicar quando o seed Java já
-- gravou. INSERT-SELECT retorna zero linhas se a role OWNER ainda não existe
-- (caso o V16 rode antes do seed Java em um tenant novo, o que é OK — o
-- seed Java cuida).

INSERT INTO role_permission (role_id, permission_code)
SELECT id, 'tenant.config.consultar'
FROM role
WHERE code = 'OWNER'
ON CONFLICT (role_id, permission_code) DO NOTHING;

INSERT INTO role_permission (role_id, permission_code)
SELECT id, 'tenant.config.editar'
FROM role
WHERE code = 'OWNER'
ON CONFLICT (role_id, permission_code) DO NOTHING;
