package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.PriceTableId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link PriceTableId} não encontrou tabela no tenant. */
public class PriceTableNotFoundException extends DomainException {

  private final PriceTableId priceTableId;

  public PriceTableNotFoundException(PriceTableId priceTableId) {
    super(
        "produto.price-table-not-found",
        HttpStatus.NOT_FOUND,
        "Tabela de preço não encontrada",
        "Não existe tabela de preço com este id neste tenant.");
    this.priceTableId = priceTableId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("priceTableId", priceTableId.value().toString());
  }
}
