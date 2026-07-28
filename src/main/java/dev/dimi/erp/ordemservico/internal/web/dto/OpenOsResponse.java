package dev.dimi.erp.ordemservico.internal.web.dto;

import dev.dimi.erp.ordemservico.ServiceOrderId;
import java.util.UUID;

public record OpenOsResponse(UUID id) {
  public static OpenOsResponse of(ServiceOrderId id) {
    return new OpenOsResponse(id.value());
  }
}
