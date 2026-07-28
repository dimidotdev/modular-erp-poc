package dev.dimi.erp.compras;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Recebimento físico registrado (RN-COM-012). Consumido por {@code financeiro} (cria MVF a pagar) e
 * {@code auditoria}. INBOUND no estoque já foi gravado pelo service na mesma transação via {@code
 * StockInventoryPort.receive} — listener não toca em estoque.
 *
 * <p>{@code supplierId} e {@code paymentConditionId} carregados a partir do {@link PurchaseOrder}
 * associado — listener financeiro precisa para preencher counterparty e (futuro) derivar
 * parcelamento.
 */
public record PurchaseReceivedEvent(
    PurchaseReceiptId receiptId,
    PurchaseOrderId orderId,
    TenantId tenantId,
    BranchId branchId,
    PersonId supplierId,
    UUID paymentConditionId,
    BigDecimal total,
    UserId receivedBy,
    Instant occurredAt) {}
