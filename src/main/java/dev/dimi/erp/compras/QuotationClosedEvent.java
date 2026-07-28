package dev.dimi.erp.compras;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Cotação encerrada — respostas dos fornecedores estão coletadas e prontas para decisão manual
 * (RN-COM-007/008). Geração de PCs a partir daqui é separada (endpoint {@code
 * /orders/from-quotation/{id}}).
 */
public record QuotationClosedEvent(
    QuotationId quotationId,
    TenantId tenantId,
    BranchId branchId,
    UserId closedBy,
    Instant occurredAt) {}
