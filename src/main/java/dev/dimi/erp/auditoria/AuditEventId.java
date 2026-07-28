package dev.dimi.erp.auditoria;

import com.fasterxml.jackson.annotation.JsonValue;
import com.github.f4b6a3.uuid.UuidCreator;
import java.util.Objects;
import java.util.UUID;

/**
 * ID tipado para {@code audit.audit_event}. Serializa como string UUID.
 *
 * <p>Embora o {@link dev.dimi.erp.auditoria.internal.application.AuditWriter} gere o UUID via
 * {@link UuidCreator#getTimeOrderedEpoch()} dentro do {@code INSERT}, expomos {@link #newId()} para
 * casos onde o caller precisa pré-alocar o id (ex.: endpoints que retornam o id imediatamente). A
 * unicidade segue o mesmo esquema (UUID v7 ordenado por tempo).
 */
public record AuditEventId(@JsonValue UUID value) {

  public AuditEventId {
    Objects.requireNonNull(value, "audit event id é obrigatório");
  }

  public static AuditEventId newId() {
    return new AuditEventId(UuidCreator.getTimeOrderedEpoch());
  }

  public static AuditEventId of(String raw) {
    return new AuditEventId(UUID.fromString(raw));
  }
}
