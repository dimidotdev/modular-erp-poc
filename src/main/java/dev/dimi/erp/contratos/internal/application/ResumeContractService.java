package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractNotFoundException;
import dev.dimi.erp.contratos.ContractResumedEvent;
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
 * Retoma contrato {@code SUSPENDED → ACTIVE}. A partir de {@code occurredAt} o job de billing
 * (slice 10.2) volta a gerar ciclos. Transição inválida sobe como {@code
 * InvalidContractTransitionException} via {@link Contract#resume(UserId)}.
 */
@Service
public class ResumeContractService {

  private static final Logger log = LoggerFactory.getLogger(ResumeContractService.class);

  private final ContractRepository contracts;
  private final ApplicationEventPublisher events;

  public ResumeContractService(ContractRepository contracts, ApplicationEventPublisher events) {
    this.contracts = contracts;
    this.events = events;
  }

  @Transactional
  public void resume(ContractId id, UserId by) {
    Contract contract =
        contracts.findById(id.value()).orElseThrow(() -> new ContractNotFoundException(id));

    contract.resume(by);
    contracts.save(contract);

    events.publishEvent(
        new ContractResumedEvent(contract.typedId(), currentTenantId(), by, Instant.now()));

    log.info(
        "Contrato retomado contractId={} number={} resumedBy={}",
        contract.typedId(),
        contract.getNumber(),
        by);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
