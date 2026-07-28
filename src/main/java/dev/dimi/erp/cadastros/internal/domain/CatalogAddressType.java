package dev.dimi.erp.cadastros.internal.domain;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.NoArgsConstructor;

/** Tipo de endereço (COMERCIAL, RESIDENCIAL, ENTREGA, COBRANCA). */
@Entity
@Table(name = "catalog_address_type")
@NoArgsConstructor
public class CatalogAddressType extends BaseCatalog {

  public static CatalogAddressType create(String code, String name) {
    CatalogAddressType c = new CatalogAddressType();
    c.init(code, name);
    return c;
  }
}
