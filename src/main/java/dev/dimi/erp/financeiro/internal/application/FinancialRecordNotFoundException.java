package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.FinancialRecordId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** MVF não encontrada. */
public class FinancialRecordNotFoundException extends DomainException {

  private final FinancialRecordId recordId;

  public FinancialRecordNotFoundException(FinancialRecordId recordId) {
    super(
        "financeiro.record-not-found",
        HttpStatus.NOT_FOUND,
        "MVF não encontrada",
        "Não há MVF com id=" + recordId.value());
    this.recordId = recordId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("recordId", recordId.value());
  }
}
