package dev.dimi.erp.auditoria;

/**
 * Categorias de evento auditável. Espelha o CHECK em {@code audit.audit_event.event_category} (V4
 * global). Doc canônica: {@code docs/domain/19-auditoria.md}.
 */
public enum AuditCategory {
  AUTH,
  ADMIN,
  FISCAL,
  FINANCIAL,
  STOCK,
  LGPD,
  PLATFORM_ADMIN,
  OS,
  CONTRACT,
  SYSTEM
}
