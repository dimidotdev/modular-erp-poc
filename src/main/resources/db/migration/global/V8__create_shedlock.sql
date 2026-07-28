-- ShedLock distributed lock table — usado pelos cron jobs para evitar execução duplicada
-- quando múltiplas instâncias do app rodarem em paralelo.
-- Vive em schema 'platform' (cross-tenant), pois jobs são globais por instância.
-- Doc: https://github.com/lukas-krecan/ShedLock

CREATE TABLE platform.shedlock (
    name        varchar(64) PRIMARY KEY,
    lock_until  timestamp(3) NOT NULL,
    locked_at   timestamp(3) NOT NULL,
    locked_by   varchar(255) NOT NULL
);
