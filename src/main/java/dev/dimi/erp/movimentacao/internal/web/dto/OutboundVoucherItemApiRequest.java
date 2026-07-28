package dev.dimi.erp.movimentacao.internal.web.dto;

import dev.dimi.erp.movimentacao.internal.application.IssueOutboundVoucherCommand;
import dev.dimi.erp.produto.PackagingId;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.util.UUID;

/** Linha de saída avulsa. */
public record OutboundVoucherItemApiRequest(
    @NotNull UUID packagingId, @NotNull @Positive BigDecimal quantity) {

  public IssueOutboundVoucherCommand.Item toCommand() {
    return new IssueOutboundVoucherCommand.Item(new PackagingId(packagingId), quantity);
  }
}
