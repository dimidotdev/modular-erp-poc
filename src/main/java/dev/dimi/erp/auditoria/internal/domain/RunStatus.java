package dev.dimi.erp.auditoria.internal.domain;

/**
 * Estado de uma execução do job de retenção. Espelha o CHECK em {@code audit.retention_run.status}
 * (V9 global).
 *
 * <ul>
 *   <li>{@code RUNNING}: job em andamento (lock ativo via ShedLock).
 *   <li>{@code SUCCESS}: terminou sem erros — contagens finais persistidas.
 *   <li>{@code FAILED}: erro durante execução; {@code error_detail} contém o stacktrace resumido.
 * </ul>
 */
public enum RunStatus {
  RUNNING,
  SUCCESS,
  FAILED
}
