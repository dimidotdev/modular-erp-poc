package dev.dimi.erp.movimentacao;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Pedido convertido em venda. {@link SalesOrderId} fica em {@link SalesOrderStatus#CONVERTED}; a
 * venda criada (em {@link SaleStatus#OPEN}) recebe back-reference via {@code sale.sales_order_id}.
 */
public record SalesOrderConvertedEvent(
    SalesOrderId salesOrderId,
    SaleId saleId,
    TenantId tenantId,
    UserId convertedBy,
    Instant occurredAt) {}
