package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Endereço criado para uma pessoa. Consumido por {@code auditoria} (tipo {@code
 * PERSON_ADDRESS_CREATED}).
 */
public record PersonAddressCreatedEvent(
    PersonId personId,
    PersonAddressId addressId,
    TenantId tenantId,
    boolean defaultAddress,
    UserId createdBy,
    Instant occurredAt) {}
