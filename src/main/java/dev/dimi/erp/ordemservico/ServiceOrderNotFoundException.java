package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** OS referenciada por ID não existe no tenant atual. */
public class ServiceOrderNotFoundException extends DomainException {

  public ServiceOrderNotFoundException(ServiceOrderId id) {
    super(
        "os.not-found",
        HttpStatus.NOT_FOUND,
        "Ordem de Serviço não encontrada",
        "Não existe OS com id " + id.value() + " no tenant atual.");
  }
}
