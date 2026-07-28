package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.List;

/**
 * Nova pessoa (PF ou PJ) registrada no tenant via {@code POST /api/v1/cadastros/pessoas}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code CADASTRO}, tipo {@code PERSON_REGISTERED}).
 *
 * @param personId id da pessoa criada
 * @param tenantId tenant onde a pessoa foi criada
 * @param personType {@code PF} ou {@code PJ}
 * @param tradeName nome/razão social informado
 * @param document CPF ou CNPJ apenas dígitos (vazio quando anônimo)
 * @param modalities modalidades atribuídas no registro (ex: {@code CUSTOMER, SUPPLIER})
 * @param registeredBy administrador que disparou o cadastro
 * @param occurredAt timestamp do registro
 */
public record PersonRegisteredEvent(
    PersonId personId,
    TenantId tenantId,
    PersonType personType,
    String tradeName,
    String document,
    List<Modality> modalities,
    UserId registeredBy,
    Instant occurredAt) {

  public PersonRegisteredEvent {
    modalities = modalities == null ? List.of() : List.copyOf(modalities);
  }
}
