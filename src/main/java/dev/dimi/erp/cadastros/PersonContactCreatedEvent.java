package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/** Contato criado para uma pessoa. */
public record PersonContactCreatedEvent(
    PersonId personId,
    PersonContactId contactId,
    TenantId tenantId,
    boolean primaryContact,
    UserId createdBy,
    Instant occurredAt) {}
