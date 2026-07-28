package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.QuotationStatus;
import dev.dimi.erp.compras.internal.application.QuotationSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Response de cotação com fornecedores convidados + respostas. */
public record QuotationApiResponse(
    QuotationId id,
    long number,
    PurchaseRequestId requestId,
    BranchId branchId,
    QuotationStatus status,
    LocalDate deadline,
    Instant createdAt,
    Instant closedAt,
    List<QuotationSupplierApiResponse> suppliers,
    List<QuotationResponseApiResponse> responses) {

  public static QuotationApiResponse from(QuotationSummary s) {
    return new QuotationApiResponse(
        s.id(),
        s.number(),
        s.requestId(),
        s.branchId(),
        s.status(),
        s.deadline(),
        s.createdAt(),
        s.closedAt(),
        s.suppliers().stream().map(QuotationSupplierApiResponse::from).toList(),
        s.responses().stream().map(QuotationResponseApiResponse::from).toList());
  }
}
