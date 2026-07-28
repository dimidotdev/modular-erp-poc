package dev.dimi.erp.ordemservico;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * OS faturada — RN-OS-BIL. Marco do fluxo de faturamento: NFe e/ou NFSe já foram autorizadas via
 * {@code FiscalProvider} e o registro {@code service_order_billing} foi persistido. Evento
 * "gordinho" para que os consumers não precisem voltar à OS via QueryPort.
 *
 * <p>Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code financeiro} — cria MVF a receber + parcelas conforme {@code paymentConditionId}
 *       (null tolerado; listener decide fallback à vista).
 *   <li>{@code auditoria} — registra.
 * </ul>
 *
 * <p>{@code nfeInvoiceId} é {@code null} quando a OS só carrega serviços; {@code nfseInvoiceId} é
 * {@code null} quando só há produtos consumidos. Ao menos um dos dois é não-nulo.
 */
public record OsBilledEvent(
    BillingId billingId,
    ServiceOrderId osId,
    TenantId tenantId,
    BranchId branchId,
    PersonId customerId,
    UUID nfeInvoiceId,
    UUID nfseInvoiceId,
    BigDecimal productsAmount,
    BigDecimal servicesAmount,
    BigDecimal total,
    UUID paymentConditionId,
    UserId issuedBy,
    Instant occurredAt) {}
