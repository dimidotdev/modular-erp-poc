package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.BillingCycleId;
import dev.dimi.erp.contratos.BillingCycleNotFoundException;
import dev.dimi.erp.contratos.CycleAlreadyGeneratedException;
import dev.dimi.erp.contratos.internal.domain.BillingCycle;
import dev.dimi.erp.contratos.internal.persistence.BillingCycleRepository;
import dev.dimi.erp.identidade.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Regenera manualmente um ciclo {@code FAILED}. Delegado ao endpoint REST (controller controla a
 * permission {@code contrato.regenerar-ciclo}); aqui só validamos elegibilidade e chamamos {@link
 * GenerateInvoiceForCycleService#generateForCycle}.
 *
 * <p>Outros estados ({@code GENERATED}, {@code PENDING}, {@code SKIPPED}) sobem {@link
 * CycleAlreadyGeneratedException} (HTTP 422) — só ciclo em {@code FAILED} é elegível: {@code
 * GENERATED} já produziu MVF, {@code PENDING} ainda será processado pelo job de rotina, {@code
 * SKIPPED} foi ignorado intencionalmente.
 *
 * <p>O serviço delegado já é {@code @Transactional} e idempotente — não precisa de tx adicional
 * aqui; nova chamada {@code Propagation.REQUIRED} aninha-se na mesma tx.
 */
@Service
public class RegenerateBillingCycleService {

  private static final Logger log = LoggerFactory.getLogger(RegenerateBillingCycleService.class);

  private final BillingCycleRepository cycles;
  private final GenerateInvoiceForCycleService generateService;

  public RegenerateBillingCycleService(
      BillingCycleRepository cycles, GenerateInvoiceForCycleService generateService) {
    this.cycles = cycles;
    this.generateService = generateService;
  }

  @Transactional
  public void regenerate(BillingCycleId cycleId, UserId actor) {
    BillingCycle cycle =
        cycles
            .findById(cycleId.value())
            .orElseThrow(() -> new BillingCycleNotFoundException(cycleId));

    if (!cycle.canRegenerate()) {
      throw new CycleAlreadyGeneratedException(cycleId, cycle.getStatus());
    }

    log.info("Regenerating cycle {} (was {}) actor={}", cycleId.value(), cycle.getStatus(), actor);
    generateService.generateForCycle(cycleId, actor);
  }
}
