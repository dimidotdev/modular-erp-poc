package dev.dimi.erp.shared.multitenancy;

import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Descreve um tenant ativo no contexto de uma operação.
 *
 * <p>Imutável. Carrega apenas o necessário para resolver o schema do banco e identificar o tenant
 * em logs/eventos. Outros dados do tenant (legal_name, status, plan) vivem em {@code shared.tenant}
 * e são consultados via repository quando necessário.
 */
public record Tenant(UUID id, String slug, String schemaName) {

  private static final Pattern SCHEMA_NAME = Pattern.compile("^tenant_[a-z0-9_]+$");
  private static final Pattern SLUG = Pattern.compile("^[a-z][a-z0-9-]{1,62}[a-z0-9]$");

  public Tenant {
    Objects.requireNonNull(id, "tenant id é obrigatório");
    Objects.requireNonNull(slug, "tenant slug é obrigatório");
    Objects.requireNonNull(schemaName, "tenant schemaName é obrigatório");
    if (!SLUG.matcher(slug).matches()) {
      throw new IllegalArgumentException("tenant slug inválido: " + slug);
    }
    if (!SCHEMA_NAME.matcher(schemaName).matches()) {
      throw new IllegalArgumentException("schemaName deve casar com tenant_*: " + schemaName);
    }
  }
}
