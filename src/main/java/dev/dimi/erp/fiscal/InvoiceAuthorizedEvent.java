package dev.dimi.erp.fiscal;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Documento fiscal autorizado pela SEFAZ. Consumidores conhecidos:
 *
 * <ul>
 *   <li>{@code movimentacao}: {@code InvoiceAuthorizedSaleListener} atualiza {@code
 *       sale.invoice_id} quando {@code originType=SALE}.
 *   <li>{@code auditoria}: registra com categoria {@code FISCAL}.
 *   <li>{@code financeiro} (futuro): pode gerar MVF caso a origem não tenha gerado.
 * </ul>
 *
 * <p>O evento inclui os campos necessários aos consumidores para evitar consultas síncronas entre
 * módulos após a autorização.
 */
public record InvoiceAuthorizedEvent(
    InvoiceId invoiceId,
    TenantId tenantId,
    BranchId branchId,
    FiscalModel model,
    String series,
    long number,
    String fiscalKey,
    FiscalOriginType originType,
    UUID originId,
    BigDecimal total,
    UserId authorizedBy,
    Instant occurredAt) {}
