-- =============================================================================
-- Slice 3.2 — Cadastros (services)
--
-- Ajuste dos índices únicos de `person` para refletir a RN-CAD-001:
-- consumidor final (`00000000000` / `00000000000000`) é sentinela compartilhada e
-- precisa permitir múltiplas linhas (uma para cada venda de consumidor não
-- identificado). A V3 original aplicava uniqueness sobre todo cpf/cnpj não-anônimo,
-- o que bloquearia o segundo registro de consumidor final.
--
-- Mantém a uniqueness para CPF/CNPJ reais de pessoas não-anonimizadas — o caso
-- ordinário que o índice precisa proteger.
-- =============================================================================

DROP INDEX IF EXISTS uq_person_cpf;
DROP INDEX IF EXISTS uq_person_cnpj;

CREATE UNIQUE INDEX uq_person_cpf ON person(cpf)
    WHERE cpf IS NOT NULL
      AND cpf <> '00000000000'
      AND anonymized = false;

CREATE UNIQUE INDEX uq_person_cnpj ON person(cnpj)
    WHERE cnpj IS NOT NULL
      AND cnpj <> '00000000000000'
      AND anonymized = false;
