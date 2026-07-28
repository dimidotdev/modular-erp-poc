-- login_attempt: o query de contagem de falhas usa LOWER(username) para ser
-- case-insensitive, mas o índice original (idx_login_attempt_user_time) é em
-- (username, attempted_at DESC) sem LOWER — defeating-the-index, vira scan da
-- partição do dia. Trocamos por índice funcional. Dados continuam armazenados
-- preservando a caixa original (forense).

DROP INDEX IF EXISTS idx_login_attempt_user_time;

CREATE INDEX idx_login_attempt_user_lower_time
    ON login_attempt (LOWER(username), attempted_at DESC);
