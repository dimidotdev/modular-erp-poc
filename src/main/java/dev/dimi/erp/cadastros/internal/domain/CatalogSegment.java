package dev.dimi.erp.cadastros.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** Segmento de atuação da pessoa (ex: VAREJO, ATACADO, INDUSTRIA). */
@Entity
@Table(name = "catalog_segment")
@NoArgsConstructor
public class CatalogSegment extends BaseCatalog {

  public static CatalogSegment create(String code, String name) {
    CatalogSegment c = new CatalogSegment();
    c.init(code, name);
    return c;
  }
}
