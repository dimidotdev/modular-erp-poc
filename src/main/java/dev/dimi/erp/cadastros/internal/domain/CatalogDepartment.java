package dev.dimi.erp.cadastros.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** Departamento (COMERCIAL, FINANCEIRO, FISCAL, ESTOQUE, ADMINISTRATIVO). */
@Entity
@Table(name = "catalog_department")
@NoArgsConstructor
public class CatalogDepartment extends BaseCatalog {

  public static CatalogDepartment create(String code, String name) {
    CatalogDepartment c = new CatalogDepartment();
    c.init(code, name);
    return c;
  }
}
