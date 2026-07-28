package dev.dimi.erp.auditoria;

/**
 * Severidade do evento auditável. Espelha o CHECK em {@code audit.audit_event.severity} (V4
 * global).
 *
 * <ul>
 *   <li>{@code INFO}: rotina (login, criação de cadastro).
 *   <li>{@code WARN}: suspeito ou recuperável (login falhou, refresh expirado).
 *   <li>{@code CRITICAL}: alerta humano (reuse de token, anonymization, cancel de NFe).
 * </ul>
 */
public enum AuditSeverity {
  INFO,
  WARN,
  CRITICAL
}
