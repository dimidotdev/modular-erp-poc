package dev.dimi.erp.auditoria;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/**
 * Evento de auditoria referenciado por id não existe — ou nunca existiu, ou pertence a outro tenant
 * (o controller traduz "tenant errado" para a mesma 404 a fim de não vazar a existência do id em
 * outros tenants).
 */
public class AuditEventNotFoundException extends DomainException {

  public AuditEventNotFoundException(UUID id) {
    super(
        "audit-event.not-found",
        HttpStatus.NOT_FOUND,
        "Evento de auditoria não encontrado",
        "Não existe evento de auditoria com id " + id + " no tenant atual.");
  }
}
