package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de editar ou desativar entrada de catálogo {@code system=true}. RN-CAD-013: seeds são
 * imutáveis para preservar integridade referencial entre tenants.
 */
public class SystemCatalogEntryImmutableException extends DomainException {

  private final CatalogType type;
  private final UUID id;

  public SystemCatalogEntryImmutableException(CatalogType type, UUID id) {
    super(
        "cadastros.system-catalog-immutable",
        HttpStatus.CONFLICT,
        "Catálogo de sistema imutável",
        "Entradas system do catálogo não podem ser editadas ou desativadas (RN-CAD-013).");
    this.type = type;
    this.id = id;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("catalogType", type.slug(), "id", id.toString());
  }
}
