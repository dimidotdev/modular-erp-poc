package dev.dimi.erp.contratos;

import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * Tentativa de regenerar um ciclo que não está em {@code FAILED}. Só ciclos {@code FAILED} são
 * elegíveis para regenerate manual — {@code GENERATED} já produziu MVF, {@code PENDING} ainda será
 * processado pelo job, {@code SKIPPED} foi ignorado intencionalmente.
 */
public class CycleAlreadyGeneratedException extends DomainException {

  private final BillingCycleId id;
  private final BillingCycleStatus status;

  public CycleAlreadyGeneratedException(BillingCycleId id, BillingCycleStatus status) {
    super(
        "billing-cycle.already-generated",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "Ciclo não regenerável",
        "Ciclo "
            + id.value()
            + " está em status "
            + status
            + " — regenerate só é permitido em FAILED.");
    this.id = id;
    this.status = status;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of("cycleId", id.value().toString(), "status", status.name());
  }
}
