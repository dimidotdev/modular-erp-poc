package dev.dimi.erp.movimentacao;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/** Pedido aprovado — fica disponível para conversão em venda (RN-MOV-002). */
public record SalesOrderApprovedEvent(
    SalesOrderId salesOrderId, TenantId tenantId, UserId approvedBy, Instant occurredAt) {}
