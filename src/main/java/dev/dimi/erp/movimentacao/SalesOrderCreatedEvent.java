package dev.dimi.erp.movimentacao;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Novo pedido de venda criado em DRAFT. Consumido por {@code auditoria}.
 *
 * @param salesOrderId id do pedido criado
 * @param tenantId tenant
 * @param branchId filial emitente
 * @param customerId cliente
 * @param number número sequencial dentro da filial
 * @param total total inicial (zero quando criado sem itens)
 * @param createdBy autor
 * @param occurredAt timestamp
 */
public record SalesOrderCreatedEvent(
    SalesOrderId salesOrderId,
    TenantId tenantId,
    BranchId branchId,
    PersonId customerId,
    long number,
    BigDecimal total,
    UserId createdBy,
    Instant occurredAt) {}
