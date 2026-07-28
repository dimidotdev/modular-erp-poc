package dev.dimi.erp.shared.modularization.internal.web.dto;

import jakarta.validation.constraints.NotNull;

/** Payload de {@code PATCH /api/v1/tenants/me/modules/{code}}. */
public record ToggleModuleRequest(@NotNull Boolean enabled) {}
