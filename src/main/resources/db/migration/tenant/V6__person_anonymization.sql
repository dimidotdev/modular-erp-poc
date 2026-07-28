-- ============================================================================
-- V6 — Anonimização LGPD: relaxar XOR de documento e seed da modalidade ANONYMIZED.
--
-- A constraint ck_person_document_xor original exige que cpf/cnpj sejam NOT NULL
-- conforme o type. Para suportar RN-CAD-011 (anonimização), permitimos NULL nos
-- documentos quando anonymized=true — caso contrário o XOR continua valendo.
-- ============================================================================

ALTER TABLE person DROP CONSTRAINT ck_person_document_xor;

ALTER TABLE person ADD CONSTRAINT ck_person_document_xor
    CHECK (
        anonymized = true
        OR (type='PF' AND cpf IS NOT NULL AND cnpj IS NULL)
        OR (type='PJ' AND cnpj IS NOT NULL AND cpf IS NULL)
    );

COMMENT ON CONSTRAINT ck_person_document_xor ON person IS
    'XOR documento/tipo. Relaxa para anonimizados (cpf/cnpj podem ser NULL após RN-CAD-011).';
