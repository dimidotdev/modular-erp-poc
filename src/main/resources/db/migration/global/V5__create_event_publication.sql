-- shared.event_publication — Outbox do Spring Modulith para eventos de domínio.
--
-- Persistido pelo @ApplicationModuleListener com garantia at-least-once:
--   - Linha inserida no commit da transação que publicou o evento.
--   - completion_date NULL = ainda não processado pelo listener.
--   - completion_date NOT NULL = listener concluiu com sucesso.
--   - Reprocessamento periódico (incompleteEventPublications) tenta os incompletos.
--
-- Schema espera-se compatível com JpaEventPublication do spring-modulith-events-jpa.
-- Documentação: docs/architecture/01-arquitetura.md (seção "Outbox in-process").

CREATE TABLE shared.event_publication (
    id               uuid NOT NULL,
    publication_date timestamptz NOT NULL,
    completion_date  timestamptz,
    listener_id      text NOT NULL,
    event_type       text NOT NULL,
    serialized_event text NOT NULL,
    PRIMARY KEY (id)
);

-- Lookup principal: "quais eventos ainda não foram processados?"
CREATE INDEX idx_event_publication_pending
    ON shared.event_publication(publication_date)
    WHERE completion_date IS NULL;

-- Lookup secundário usado por queries de retry/diagnóstico do Modulith.
CREATE INDEX idx_event_publication_listener_event
    ON shared.event_publication(listener_id, event_type);

COMMENT ON TABLE shared.event_publication IS
    'Outbox do Spring Modulith. completion_date NULL = pendente; reprocessamento periódico tenta os pendentes.';
COMMENT ON COLUMN shared.event_publication.listener_id IS
    'Identificador único do @ApplicationModuleListener consumidor.';
COMMENT ON COLUMN shared.event_publication.event_type IS
    'FQN da classe do evento.';
