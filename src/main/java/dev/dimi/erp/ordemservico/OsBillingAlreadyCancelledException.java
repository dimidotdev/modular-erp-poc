package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Tentativa de cancelar um {@code service_order_billing} que já está cancelado — idempotência. */
public class OsBillingAlreadyCancelledException extends DomainException {

  public OsBillingAlreadyCancelledException(BillingId billingId) {
    super(
        "os.billing-already-cancelled",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Faturamento de OS já cancelado",
        "Faturamento " + billingId.value() + " já está cancelado — operação recusada.");
  }
}
