package dev.dimi.erp.financeiro;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * MVF criada (RN-FIN-004). Consumido por {@code auditoria} (registra fato contábil) e — quando o
 * adapter de notificação existir — pode acionar boleto/cobrança. {@code originType=MANUAL} =>
 * {@code originId} nulo; demais origens carregam o id do agregado fonte.
 */
public record FinancialRecordCreatedEvent(
    FinancialRecordId recordId,
    TenantId tenantId,
    BranchId branchId,
    FinancialDirection direction,
    PersonId counterpartyId,
    FinancialOriginType originType,
    UUID originId,
    BigDecimal totalValue,
    UserId createdBy,
    Instant occurredAt) {}
