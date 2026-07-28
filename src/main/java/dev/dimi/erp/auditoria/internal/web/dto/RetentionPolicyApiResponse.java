package dev.dimi.erp.auditoria.internal.web.dto;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.RetentionAction;
import dev.dimi.erp.auditoria.internal.domain.RetentionPolicy;
import java.time.Instant;

/** Representação REST de {@code audit.retention_policy}. */
public record RetentionPolicyApiResponse(
    AuditCategory category, int retainDays, RetentionAction actionAtEol, Instant updatedAt) {

  public static RetentionPolicyApiResponse from(RetentionPolicy p) {
    return new RetentionPolicyApiResponse(
        p.getCategory(), p.getRetainDays(), p.getActionAtEol(), p.getUpdatedAt());
  }
}
