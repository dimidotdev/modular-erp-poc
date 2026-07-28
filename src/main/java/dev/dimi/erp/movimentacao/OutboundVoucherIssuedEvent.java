package dev.dimi.erp.movimentacao;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Saída avulsa emitida. Estoque já foi baixado via {@code StockInventoryPort.ship} (par
 * reserve+ship transacional, mesmo padrão do fechamento de venda). RN-MOV-022: pode gerar NFe
 * conforme {@link OutboundReason} (slice fiscal).
 */
public record OutboundVoucherIssuedEvent(
    OutboundVoucherId voucherId,
    TenantId tenantId,
    BranchId branchId,
    OutboundReason reason,
    UserId issuedBy,
    Instant occurredAt) {}
