package dev.dimi.erp.contratos;

/**
 * Ciclo de vida do contrato.
 *
 * <pre>
 * DRAFT → ACTIVE → SUSPENDED ⇄ ACTIVE
 *               ↘ EXPIRED (atinge endDate sem renovação)
 *               ↘ CANCELLED (rescisão administrativa)
 *               ↘ TERMINATED_EARLY (rescisão antecipada com multa).
 * </pre>
 *
 * Valores casam com o CHECK constraint da migration V23 ({@code contract.status}).
 */
public enum ContractStatus {
  DRAFT,
  ACTIVE,
  SUSPENDED,
  EXPIRED,
  CANCELLED,
  TERMINATED_EARLY
}
