package dev.dimi.erp.identidade;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.List;

/**
 * Novo usuário criado dentro de um tenant via {@code POST /api/v1/admin/users}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code ADMIN}, tipo {@code USER_CREATED}).
 *
 * @param userId id do usuário criado
 * @param tenantId tenant onde o usuário foi criado
 * @param tenantSlug slug do tenant (humano-legível em audit summary)
 * @param username login canônico
 * @param email email canônico
 * @param assignedRoles códigos das roles atribuídas no momento da criação
 * @param createdBy id do administrador que disparou a criação
 * @param occurredAt timestamp da criação
 */
public record UserCreatedEvent(
    UserId userId,
    TenantId tenantId,
    String tenantSlug,
    String username,
    String email,
    List<String> assignedRoles,
    UserId createdBy,
    Instant occurredAt) {

  public UserCreatedEvent {
    assignedRoles = assignedRoles == null ? List.of() : List.copyOf(assignedRoles);
  }
}
