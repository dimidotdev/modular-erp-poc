package dev.dimi.erp.ordemservico;

import dev.dimi.erp.shared.exception.DomainException;
import org.springframework.http.HttpStatus;

/** Registro de {@code service_order_billing} referenciado por ID não existe no tenant atual. */
public class OsBillingNotFoundException extends DomainException {

  public OsBillingNotFoundException(BillingId billingId) {
    super(
        "os.billing-not-found",
        HttpStatus.NOT_FOUND,
        "Faturamento de OS não encontrado",
        "Não existe faturamento com id " + billingId.value() + " no tenant atual.");
  }
}
