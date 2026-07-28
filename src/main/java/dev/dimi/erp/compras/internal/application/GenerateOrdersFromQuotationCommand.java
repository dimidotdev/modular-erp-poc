package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.identidade.UserId;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Gera N PCs a partir de cotação CLOSED — uma por fornecedor selecionado (RN-COM-008). Operador
 * escolhe qual response usar de cada item. v1 simples: lista de seleções (response.id ⇒ accept).
 */
public record GenerateOrdersFromQuotationCommand(
    QuotationId quotationId, List<SupplierSelection> selections, UserId actor) {

  public record SupplierSelection(
      PersonId supplierId,
      List<UUID> selectedResponseIds,
      UUID paymentConditionId,
      LocalDate deliveryDateExpected,
      String notes) {}
}
