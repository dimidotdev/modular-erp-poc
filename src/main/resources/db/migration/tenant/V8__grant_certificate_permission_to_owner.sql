-- Slice 3.5 backfill: garante que a role OWNER do tenant tenha
-- 'filial.certificado.gerenciar'.
--
-- Tenants novos recebem essa permission via TenantProvisioner + SystemRoles,
-- mas tenants provisionados antes do V6 global precisam deste catch-up.
--
-- Idempotente:
--  - INSERT-SELECT retorna zero linhas se a role OWNER ainda não existe
--    (caso o V8 rode antes do seed Java em um tenant novo).
--  - ON CONFLICT DO NOTHING evita duplicar quando o seed Java já gravou.

INSERT INTO role_permission (role_id, permission_code)
SELECT id, 'filial.certificado.gerenciar'
FROM role
WHERE code = 'OWNER'
ON CONFLICT (role_id, permission_code) DO NOTHING;
