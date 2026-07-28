package dev.dimi.erp.compras;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Devolução a fornecedor emitida (RN-COM-020). OUTBOUND no estoque + decremento do recebido nas
 * linhas do PC são feitos na mesma transação do service para garantir atomicidade — listener
 * atualiza auditoria.
 *
 * <p>v1 ({@code slice 7.3}): listener financeiro NÃO estorna MVF original automaticamente — cálculo
 * proporcional fica para slice futuro. Operador resolve manualmente via {@code /cancel} ou {@code
 * /renegotiate}. Listener apenas registra o fato.
 */
public record SupplierReturnIssuedEvent(
    SupplierReturnId supplierReturnId,
    PurchaseReceiptId receiptId,
    TenantId tenantId,
    PersonId supplierId,
    BigDecimal total,
    String reason,
    UserId issuedBy,
    Instant occurredAt) {}
