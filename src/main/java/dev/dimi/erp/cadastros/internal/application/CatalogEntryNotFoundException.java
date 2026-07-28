package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Catálogo do tipo informado não contém entrada com o id pedido. */
public class CatalogEntryNotFoundException extends DomainException {

  private final CatalogType type;
  private final UUID id;

  public CatalogEntryNotFoundException(CatalogType type, UUID id) {
    super(
        "cadastros.catalog-entry-not-found",
        HttpStatus.NOT_FOUND,
        "Entrada de catálogo não encontrada",
        "Catálogo " + type.slug() + " não contém este id neste tenant.");
    this.type = type;
    this.id = id;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("catalogType", type.slug(), "id", id.toString());
  }
}
