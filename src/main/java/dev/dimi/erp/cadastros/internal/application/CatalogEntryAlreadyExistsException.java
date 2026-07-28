package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Tentativa de criar entrada com {@code code} já presente no catálogo (índice UNIQUE no DDL). */
public class CatalogEntryAlreadyExistsException extends DomainException {

  private final CatalogType type;
  private final String code;

  public CatalogEntryAlreadyExistsException(CatalogType type, String code) {
    super(
        "cadastros.catalog-entry-already-exists",
        HttpStatus.CONFLICT,
        "Code duplicado no catálogo",
        "Já existe entrada com este code neste catálogo.");
    this.type = type;
    this.code = code;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("catalogType", type.slug(), "code", code);
  }
}
