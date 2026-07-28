package dev.dimi.erp.compras.internal.web.dto;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.internal.application.GenerateOrdersFromQuotationCommand;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Seleção de respostas de um fornecedor em {@code /orders/from-quotation/{id}}. */
public record SupplierSelectionApiRequest(
    @NotNull UUID supplierId,
    @NotEmpty List<UUID> selectedResponseIds,
    UUID paymentConditionId,
    LocalDate deliveryDateExpected,
    @Size(max = 2000) String notes) {

  public GenerateOrdersFromQuotationCommand.SupplierSelection toCommand() {
    return new GenerateOrdersFromQuotationCommand.SupplierSelection(
        new PersonId(supplierId),
        selectedResponseIds,
        paymentConditionId,
        deliveryDateExpected,
        notes);
  }
}
