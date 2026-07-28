package dev.dimi.erp.movimentacao;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Venda fechada — RN-MOV-004. Marco do fluxo comercial: estoque já foi baixado via {@code
 * StockInventoryPort.ship} no service de fechamento, e este evento é consumido por:
 *
 * <ul>
 *   <li>{@code financeiro} (slice 7) — cria MVF a receber + parcelas conforme {@code
 *       paymentConditionId}.
 *   <li>{@code fiscal} (slice 8) — opcionalmente dispara emissão de NFe.
 *   <li>{@code auditoria} — registra.
 * </ul>
 *
 * <p>v1 carrega só os campos essenciais para os consumers atuais (financeiro precisa do total e da
 * condição de pagamento; auditoria precisa do quem/quando). Detalhamento de itens fica no banco —
 * consumers que precisarem buscam via {@code SaleQueryPort} (slice futuro) ou cross-module.
 */
public record SaleClosedEvent(
    SaleId saleId,
    TenantId tenantId,
    BranchId branchId,
    PersonId customerId,
    UUID paymentConditionId,
    BigDecimal totalGross,
    BigDecimal totalDiscount,
    BigDecimal totalFreight,
    BigDecimal total,
    UserId closedBy,
    Instant occurredAt) {}
