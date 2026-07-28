-- Slice 3.5: permission para gerenciar certificado A1 da filial.
-- BranchController.installCertificate / clearCertificate exigem essa permission
-- + step-up. Sensitive=true: cobre instalação, substituição e remoção de PFX
-- no Azure Key Vault (RN-CAD-007/008).
--
-- Esquecida no V3 quando o slice 3.5 introduziu os endpoints; este V6 corrige.

INSERT INTO shared.permission (code, display_name, description, category, is_sensitive) VALUES
    ('filial.certificado.gerenciar', 'Gerenciar certificado A1 da filial',
     'Instalar, substituir ou remover o certificado A1 (PFX) da filial no Key Vault.',
     'cadastros', true)
ON CONFLICT (code) DO NOTHING;
