package dev.dimi.erp.shared.modularization.internal.web.dto;

import dev.dimi.erp.shared.modularization.ModuleCatalog;
import java.util.List;

/**
 * Representação REST do estado de um módulo no tenant ativo. Combina metadados imutáveis do {@link
 * ModuleCatalog.ModuleDef} (display name, categoria, isCore, installed, dependsOn) com o flag
 * dinâmico {@code enabled} resolvido a partir de {@code tenant_module} (ou default do catálogo).
 */
public record ModuleStatusApiResponse(
    String code,
    String displayName,
    String category,
    boolean isCore,
    boolean installed,
    boolean enabled,
    List<String> dependsOn) {

  public static ModuleStatusApiResponse of(ModuleCatalog.ModuleDef def, boolean enabled) {
    return new ModuleStatusApiResponse(
        def.code(),
        def.displayName(),
        def.category(),
        def.isCore(),
        def.installed(),
        enabled,
        List.copyOf(def.dependsOn()));
  }
}
