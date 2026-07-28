package dev.dimi.erp.auditoria;

/**
 * Ação aplicada a linhas de {@code audit.audit_event} quando atingem o {@code retain_days} da
 * política de retenção (espelha o CHECK em {@code audit.retention_policy.action_at_eol} — V9
 * global).
 *
 * <ul>
 *   <li>{@code PURGE}: linha é fisicamente removida (DELETE). Usado para categorias de baixo valor
 *       forense além do prazo (ex.: {@code AUTH}, {@code SYSTEM}).
 *   <li>{@code ANONYMIZE}: linha permanece mas {@code user_id}, {@code actor_ip}, {@code
 *       actor_user_agent}, {@code payload} são zerados — preserva a métrica histórica sem reter
 *       dados pessoais (LGPD). Usado para categorias com valor de trilha legal (ex.: {@code
 *       FISCAL}, {@code FINANCIAL}, {@code LGPD}).
 * </ul>
 *
 * <p>É público para que UIs administrativas (gestão de política de retenção) possam tipar a escolha
 * via DTO sem importar tipo interno.
 */
public enum RetentionAction {
  PURGE,
  ANONYMIZE
}
