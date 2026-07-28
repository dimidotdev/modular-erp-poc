package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Slug informado em {@code /api/v1/cadastros/catalogs/{type}} não corresponde a um catálogo. */
public class UnknownCatalogTypeException extends DomainException {

  private final String slug;

  public UnknownCatalogTypeException(String slug) {
    super(
        "cadastros.unknown-catalog-type",
        HttpStatus.NOT_FOUND,
        "Catálogo desconhecido",
        "Tipo de catálogo desconhecido: " + slug + ".");
    this.slug = slug;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("catalogType", slug);
  }
}
