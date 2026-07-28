package dev.dimi.erp.fiscal.internal.domain;

/**
 * Tipo de evento SEFAZ persistido em {@code fiscal_event}. v1 (8.1): AUTHORIZATION + REJECTION.
 * CANCELLATION/CCE/INUTILIZATION/MANIFESTATION entram em 8.6.
 */
public enum FiscalEventType {
  AUTHORIZATION,
  REJECTION
}
