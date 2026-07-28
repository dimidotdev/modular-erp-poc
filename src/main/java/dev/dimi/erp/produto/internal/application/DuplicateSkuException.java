package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de criar embalagem com SKU já em uso. SKU é único globalmente no tenant (RN-PRD-001) —
 * idem para {@code sku_root} de produto.
 */
public class DuplicateSkuException extends DomainException {

  private final String sku;

  public DuplicateSkuException(String sku) {
    super(
        "produto.duplicate-sku",
        HttpStatus.CONFLICT,
        "SKU duplicado",
        "Já existe registro com este SKU neste tenant.");
    this.sku = sku;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("sku", sku);
  }
}
