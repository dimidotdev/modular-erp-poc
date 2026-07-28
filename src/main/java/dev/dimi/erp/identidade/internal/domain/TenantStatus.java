package dev.dimi.erp.identidade.internal.domain;

/**
 * Estados de ciclo de vida de um tenant. Conforme CHECK constraint em {@code shared.tenant.status}.
 */
public enum TenantStatus {
  PROVISIONING,
  ACTIVE,
  SUSPENDED,
  TERMINATED,
  FAILED
}
