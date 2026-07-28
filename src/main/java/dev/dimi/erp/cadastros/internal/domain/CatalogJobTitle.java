package dev.dimi.erp.cadastros.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** Cargo do funcionário (VENDEDOR, CAIXA, GERENTE, etc). */
@Entity
@Table(name = "catalog_job_title")
@NoArgsConstructor
public class CatalogJobTitle extends BaseCatalog {

  public static CatalogJobTitle create(String code, String name) {
    CatalogJobTitle c = new CatalogJobTitle();
    c.init(code, name);
    return c;
  }
}
