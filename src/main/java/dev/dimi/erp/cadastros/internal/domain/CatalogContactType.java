package dev.dimi.erp.cadastros.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** Tipo de contato (EMAIL, TELEFONE, CELULAR, WHATSAPP). */
@Entity
@Table(name = "catalog_contact_type")
@NoArgsConstructor
public class CatalogContactType extends BaseCatalog {

  public static CatalogContactType create(String code, String name) {
    CatalogContactType c = new CatalogContactType();
    c.init(code, name);
    return c;
  }
}
