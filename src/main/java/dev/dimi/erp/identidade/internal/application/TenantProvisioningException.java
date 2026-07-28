package dev.dimi.erp.identidade.internal.application;

/**
 * Falha não recuperável durante o provisionamento de um tenant. O service tenta limpar o estado
 * parcial (DROP SCHEMA, marca {@code shared.tenant.status = FAILED}) antes de relançar.
 */
public class TenantProvisioningException extends RuntimeException {

  private final String tenantSlug;

  public TenantProvisioningException(String tenantSlug, String message, Throwable cause) {
    super(message, cause);
    this.tenantSlug = tenantSlug;
  }

  public TenantProvisioningException(String tenantSlug, String message) {
    super(message);
    this.tenantSlug = tenantSlug;
  }

  public String tenantSlug() {
    return tenantSlug;
  }
}
