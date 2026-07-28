package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.QuotationStatus;
import dev.dimi.erp.compras.internal.domain.Quotation;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** Snapshot de {@code quotation} com fornecedores convidados + respostas. */
public record QuotationSummary(
    QuotationId id,
    long number,
    PurchaseRequestId requestId,
    BranchId branchId,
    QuotationStatus status,
    LocalDate deadline,
    Instant createdAt,
    Instant closedAt,
    List<QuotationSupplierSummary> suppliers,
    List<QuotationResponseSummary> responses) {

  public static QuotationSummary of(
      Quotation q,
      List<QuotationSupplierSummary> suppliers,
      List<QuotationResponseSummary> responses) {
    return new QuotationSummary(
        q.id(),
        q.getNumber(),
        q.requestIdTyped(),
        q.branchIdTyped(),
        q.getStatus(),
        q.getDeadline(),
        q.getCreatedAt(),
        q.getClosedAt(),
        suppliers,
        responses);
  }
}
