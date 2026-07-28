package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link SaleId} não encontrou venda no tenant. */
public class SaleNotFoundException extends DomainException {

  private final SaleId saleId;

  public SaleNotFoundException(SaleId saleId) {
    super(
        "movimentacao.sale-not-found",
        HttpStatus.NOT_FOUND,
        "Venda não encontrada",
        "Não existe venda com este id neste tenant.");
    this.saleId = saleId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("saleId", saleId.value().toString());
  }
}
