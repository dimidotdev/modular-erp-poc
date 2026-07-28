-- Certificado A1 armazenado como um único Azure Key Vault Secret.
--
-- O material {pfx_b64, password} permanece no gerenciador de segredos, com
-- criptografia em repouso e versionamento. O banco guarda apenas o nome do
-- secret e metadados extraídos do PKCS#12. Pré-condição: as colunas
-- substituídas devem estar vazias antes da aplicação desta migração.

ALTER TABLE branch_fiscal DROP COLUMN cert_blob_path;
ALTER TABLE branch_fiscal DROP COLUMN cert_password_enc;

ALTER TABLE branch_fiscal ADD COLUMN cert_kv_secret_name text;
ALTER TABLE branch_fiscal ADD COLUMN cert_subject_cnpj   char(14);

COMMENT ON COLUMN branch_fiscal.cert_kv_secret_name IS
    'Nome do Secret no Azure Key Vault contendo {pfx_b64, password} JSON.';
COMMENT ON COLUMN branch_fiscal.cert_subject_cnpj IS
    'CNPJ extraído do CN do certificado (deve casar com branch_fiscal.cnpj).';
