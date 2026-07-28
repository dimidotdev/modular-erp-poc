package dev.dimi.erp.contratos.internal.web.dto;

import dev.dimi.erp.contratos.AdjustmentId;
import dev.dimi.erp.contratos.AmendmentId;
import dev.dimi.erp.contratos.ContractId;
import java.util.UUID;

/**
 * Resposta enxuta para POSTs que retornam só o ID criado — contrato, aditivo, reajuste. Evita
 * proliferar três records idênticos.
 */
public record CreateResponse(UUID id) {

  public static CreateResponse of(ContractId id) {
    return new CreateResponse(id.value());
  }

  public static CreateResponse of(AmendmentId id) {
    return new CreateResponse(id.value());
  }

  public static CreateResponse of(AdjustmentId id) {
    return new CreateResponse(id.value());
  }
}
