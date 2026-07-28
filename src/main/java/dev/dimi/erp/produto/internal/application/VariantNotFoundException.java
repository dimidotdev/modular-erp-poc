package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;

/** Lookup por id de variant não encontrou registro. */
public class VariantNotFoundException extends DomainException {

  private final UUID variantId;

  public VariantNotFoundException(UUID variantId) {
    super(
        "produto.variant-not-found",
        HttpStatus.NOT_FOUND,
        "Variedade não encontrada",
        "Não existe variedade com este id neste tenant.");
    this.variantId = variantId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("variantId", variantId.toString());
  }
}
