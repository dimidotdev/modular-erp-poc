package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractNotFoundException;
import dev.dimi.erp.contratos.ContractSuspendedEvent;
import dev.dimi.erp.contratos.internal.domain.Contract;
import dev.dimi.erp.contratos.internal.persistence.ContractRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suspende contrato {@code ACTIVE → SUSPENDED}. Pausa temporária — geração de billing cycles (slice
 * 10.2) pausa até {@code ResumeContractService.resume(...)}. Transição inválida sobe como {@code
 * InvalidContractTransitionException} via {@link Contract#suspend(UserId, String)}.
 */
@Service
public class SuspendContractService {

  private static final Logger log = LoggerFactory.getLogger(SuspendContractService.class);

  private final ContractRepository contracts;
  private final ApplicationEventPublisher events;

  public SuspendContractService(ContractRepository contracts, ApplicationEventPublisher events) {
    this.contracts = contracts;
    this.events = events;
  }

  @Transactional
  public void suspend(ContractId id, String reason, UserId by) {
    Contract contract =
        contracts.findById(id.value()).orElseThrow(() -> new ContractNotFoundException(id));

    contract.suspend(by, reason);
    contracts.save(contract);

    events.publishEvent(
        new ContractSuspendedEvent(
            contract.typedId(), currentTenantId(), reason, by, Instant.now()));

    log.info(
        "Contrato suspenso contractId={} number={} reason={} suspendedBy={}",
        contract.typedId(),
        contract.getNumber(),
        reason,
        by);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
