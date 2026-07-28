package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.StockTransferId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link StockTransferId} não encontrou transferência. */
public class StockTransferNotFoundException extends DomainException {

  private final StockTransferId transferId;

  public StockTransferNotFoundException(StockTransferId transferId) {
    super(
        "movimentacao.transfer-not-found",
        HttpStatus.NOT_FOUND,
        "Transferência não encontrada",
        "Não existe transferência com este id neste tenant.");
    this.transferId = transferId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("transferId", transferId.value().toString());
  }
}
