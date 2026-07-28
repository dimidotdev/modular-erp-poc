package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.OsLaborId;
import dev.dimi.erp.ordemservico.OsProductId;
import dev.dimi.erp.ordemservico.OsServiceId;
import java.util.UUID;

/**
 * Resposta enxuta de POST de item — só ID e {@code lineNumber} (quando aplicável). Labor não tem
 * {@code lineNumber} no schema, então usa {@link #ofLabor(OsLaborId)} (lineNumber = {@code null}).
 */
public record AddItemResponse(UUID id, Integer lineNumber) {

  public static AddItemResponse ofProduct(OsProductId id, int lineNumber) {
    return new AddItemResponse(id.value(), lineNumber);
  }

  public static AddItemResponse ofService(OsServiceId id, int lineNumber) {
    return new AddItemResponse(id.value(), lineNumber);
  }

  public static AddItemResponse ofLabor(OsLaborId id) {
    return new AddItemResponse(id.value(), null);
  }
}
