package dev.dimi.erp.estoque.internal.application;

import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/** Lookup por {@link StockLocationId} não encontrou local. */
public class StockLocationNotFoundException extends DomainException {

  private final StockLocationId locationId;

  public StockLocationNotFoundException(StockLocationId locationId) {
    super(
        "estoque.location-not-found",
        HttpStatus.NOT_FOUND,
        "Local de estoque não encontrado",
        "Não existe local com este id neste tenant.");
    this.locationId = locationId;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("locationId", locationId.value().toString());
  }
}
