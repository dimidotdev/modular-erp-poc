package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.PaymentId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Pagamento financeiro não encontrado. */
public class FinancialPaymentNotFoundException extends DomainException {

  private final PaymentId paymentId;

  public FinancialPaymentNotFoundException(PaymentId paymentId) {
    super(
        "financeiro.payment-not-found",
        HttpStatus.NOT_FOUND,
        "Pagamento não encontrado",
        "Não há pagamento com id=" + paymentId.value());
    this.paymentId = paymentId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("paymentId", paymentId.value());
  }
}
