package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Ciclo de faturamento não encontrado no tenant atual. HTTP 404. */
public class BillingCycleNotFoundException extends DomainException {

  public BillingCycleNotFoundException(BillingCycleId id) {
    super(
        "billing-cycle.not-found",
        HttpStatus.NOT_FOUND,
        "Ciclo de faturamento não encontrado",
        "Ciclo de faturamento " + id.value() + " não existe no tenant atual.");
  }
}
