package dev.dimi.erp.cadastros.internal.application;

import java.util.Arrays;
import java.util.Optional;

/**
 * Identifica o catálogo destino dos endpoints {@code /api/v1/cadastros/catalogs/{type}}. O slug
 * (path) usa kebab-case (ex: {@code address-types}); o nome da tabela segue snake_case Postgres.
 *
 * <p>{@code payment-conditions} fica fora desta enum porque seu schema é mais rico (parcelas, dias)
 * — entra junto com o módulo Financeiro.
 */
public enum CatalogType {
  SEGMENT("segments"),
  REGION("regions"),
  ADDRESS_TYPE("address-types"),
  CONTACT_TYPE("contact-types"),
  JOB_TITLE("job-titles"),
  DEPARTMENT("departments"),
  MEASUREMENT_UNIT("measurement-units");

  private final String slug;

  CatalogType(String slug) {
    this.slug = slug;
  }

  public String slug() {
    return slug;
  }

  public static Optional<CatalogType> fromSlug(String slug) {
    return Arrays.stream(values()).filter(c -> c.slug.equals(slug)).findFirst();
  }
}
