package dev.dimi.erp.movimentacao.internal.application;

import dev.dimi.erp.movimentacao.OutboundVoucherId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link OutboundVoucherId} não encontrou saída avulsa. */
public class OutboundVoucherNotFoundException extends DomainException {

  private final OutboundVoucherId voucherId;

  public OutboundVoucherNotFoundException(OutboundVoucherId voucherId) {
    super(
        "movimentacao.voucher-not-found",
        HttpStatus.NOT_FOUND,
        "Saída avulsa não encontrada",
        "Não existe saída avulsa com este id neste tenant.");
    this.voucherId = voucherId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("voucherId", voucherId.value().toString());
  }
}
