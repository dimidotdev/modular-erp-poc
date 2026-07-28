package dev.dimi.erp.compras;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * PC criado (RN-COM-008/009). Consumido por {@code auditoria} e (futuro) sistema de notificação
 * para alertar fornecedor.
 */
public record PurchaseOrderCreatedEvent(
    PurchaseOrderId orderId,
    TenantId tenantId,
    BranchId branchId,
    PersonId supplierId,
    QuotationId originQuotationId,
    BigDecimal total,
    UserId createdBy,
    Instant occurredAt) {}
