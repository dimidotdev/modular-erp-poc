package dev.dimi.erp.ordemservico;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * Item de serviço adicionado à OS. Consumidores conhecidos: {@code auditoria}; no futuro {@code
 * fiscal} usará para preparar geração de NFSe junto com NFe de produtos (saga 9.2b).
 *
 * <p>Evento gordinho — carrega {@code serviceCode} (LC 116), {@code netValue} e {@code issqnValue}
 * para consumer dispensar QueryPort.
 */
public record OsServiceAddedEvent(
    OsServiceId serviceId,
    ServiceOrderId osId,
    TenantId tenantId,
    String serviceCode,
    BigDecimal netValue,
    BigDecimal issqnValue,
    UserId actor,
    Instant occurredAt) {}
