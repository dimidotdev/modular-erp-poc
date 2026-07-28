package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.SaleReturnId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link SaleReturnId} não encontrou devolução. */
public class SaleReturnNotFoundException extends DomainException {

  private final SaleReturnId returnId;

  public SaleReturnNotFoundException(SaleReturnId returnId) {
    super(
        "movimentacao.return-not-found",
        HttpStatus.NOT_FOUND,
        "Devolução não encontrada",
        "Não existe devolução com este id neste tenant.");
    this.returnId = returnId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("returnId", returnId.value().toString());
  }
}
