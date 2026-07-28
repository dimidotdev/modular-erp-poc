package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de editar campos estruturais do contrato fora de {@code DRAFT}. Após {@code ACTIVE}
 * mudanças contratuais passam por {@code amendment}/{@code adjustment}, não por edição direta.
 */
public class ContractNotEditableException extends DomainException {

  private final ContractId id;
  private final ContractStatus status;

  public ContractNotEditableException(ContractId id, ContractStatus status) {
    super(
        "contract.not-editable",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Contrato não editável",
        "Contrato "
            + id.value()
            + " está em status "
            + status
            + " — edição direta só é permitida em DRAFT. Use aditivo (amendment) ou reajuste"
            + " (adjustment).");
    this.id = id;
    this.status = status;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("contractId", id.value().toString(), "status", status.name());
  }
}
