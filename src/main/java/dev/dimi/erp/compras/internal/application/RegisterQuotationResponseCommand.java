package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.List;

/**
 * Registra resposta de fornecedor — substitui todas as linhas anteriores desse supplier para essa
 * quotation (replaceAll comportamento). RN-COM-006: fornecedor pode omitir itens (lista parcial).
 */
public record RegisterQuotationResponseCommand(
    QuotationId quotationId, PersonId supplierId, List<Line> lines, UserId actor) {

  public record Line(
      int requestItemLine,
      PackagingId packagingId,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      String notes) {}
}
