package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** RN-PRD-007: barcode é único globalmente no tenant quando informado. */
public class DuplicateBarcodeException extends DomainException {

  private final String barcode;

  public DuplicateBarcodeException(String barcode) {
    super(
        "produto.duplicate-barcode",
        HttpStatus.CONFLICT,
        "Código de barras duplicado",
        "Já existe embalagem com este código de barras neste tenant.");
    this.barcode = barcode;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("barcode", barcode);
  }
}
