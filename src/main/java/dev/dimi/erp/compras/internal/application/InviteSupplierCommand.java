package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.util.UUID;

/** Convida fornecedor para participar de cotação. */
public record InviteSupplierCommand(
    QuotationId quotationId,
    PersonId supplierId,
    UUID paymentConditionId,
    Integer deliveryDays,
    BigDecimal freightValue,
    String notes,
    UserId actor) {}
