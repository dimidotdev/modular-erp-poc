package dev.dimi.erp.movimentacao;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Venda aberta em rascunho — ainda não baixa estoque nem gera MVF (RN-MOV-004). Consumido por
 * {@code auditoria}.
 */
public record SaleOpenedEvent(
    SaleId saleId,
    TenantId tenantId,
    BranchId branchId,
    PersonId customerId,
    SalesOrderId originSalesOrderId,
    long number,
    BigDecimal total,
    UserId openedBy,
    Instant occurredAt) {}
