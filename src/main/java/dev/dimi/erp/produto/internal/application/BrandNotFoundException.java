package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link BrandId} não encontrou marca no catálogo do tenant. */
public class BrandNotFoundException extends DomainException {

  private final BrandId brandId;

  public BrandNotFoundException(BrandId brandId) {
    super(
        "produto.brand-not-found",
        HttpStatus.NOT_FOUND,
        "Marca não encontrada",
        "Não existe marca com este id no catálogo deste tenant.");
    this.brandId = brandId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("brandId", brandId.value().toString());
  }
}
