package dev.dimi.erp.cadastros.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** Região comercial (ex: BR, SUL, NORDESTE). */
@Entity
@Table(name = "catalog_region")
@NoArgsConstructor
public class CatalogRegion extends BaseCatalog {

  public static CatalogRegion create(String code, String name) {
    CatalogRegion c = new CatalogRegion();
    c.init(code, name);
    return c;
  }
}
