package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link ProductId} não encontrou produto no tenant atual. */
public class ProductNotFoundException extends DomainException {

  private final ProductId productId;

  public ProductNotFoundException(ProductId productId) {
    super(
        "produto.product-not-found",
        HttpStatus.NOT_FOUND,
        "Produto não encontrado",
        "Não existe produto com este id neste tenant.");
    this.productId = productId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("productId", productId.value().toString());
  }
}
