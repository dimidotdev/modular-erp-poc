package dev.dimi.erp.cadastros.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** Unidade de medida (UN, KG, L, M, PC, etc). Usado também pelo módulo Produto. */
@Entity
@Table(name = "catalog_measurement_unit")
@NoArgsConstructor
public class CatalogMeasurementUnit extends BaseCatalog {

  public static CatalogMeasurementUnit create(String code, String name) {
    CatalogMeasurementUnit c = new CatalogMeasurementUnit();
    c.init(code, name);
    return c;
  }
}
