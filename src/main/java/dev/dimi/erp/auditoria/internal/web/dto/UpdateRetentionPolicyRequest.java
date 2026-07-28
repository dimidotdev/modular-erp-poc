package dev.dimi.erp.auditoria.internal.web.dto;

import dev.dimi.erp.auditoria.RetentionAction;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Payload de PATCH para {@code audit.retention_policy}. Ambos os campos são exigidos — o cliente
 * decide explicitamente os novos valores; não há merge parcial em V1.
 */
public record UpdateRetentionPolicyRequest(
    @Min(1) int retainDays, @NotNull RetentionAction actionAtEol) {}
