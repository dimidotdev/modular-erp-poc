package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractNotFoundException;
import dev.dimi.erp.contratos.ContractStatus;
import dev.dimi.erp.contratos.ContractTerminatedEvent;
import dev.dimi.erp.contratos.InvalidContractTransitionException;
import dev.dimi.erp.contratos.internal.domain.Contract;
import dev.dimi.erp.contratos.internal.persistence.ContractRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.EnumSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Encerra contrato. Aceita {@code ACTIVE → {EXPIRED, TERMINATED_EARLY}} ou {@code SUSPENDED →
 * {EXPIRED, TERMINATED_EARLY}}. Outros status sobem {@link InvalidContractTransitionException}.
 *
 * <p>Quando {@code earlyTermination=true} e {@link Contract#getCancellationFeeValue()} {@code > 0},
 * o evento carrega {@code cancellationFee} para o listener financeiro (slice 10.2) gerar MVF de
 * multa. Caso contrário {@code cancellationFee} vai como {@code BigDecimal.ZERO}.
 *
 * <p>TODO 10.2: {@code ContractTerminatedEvent} listener em {@code financeiro} cria MVF de multa.
 */
@Service
public class TerminateContractService {

  private static final Logger log = LoggerFactory.getLogger(TerminateContractService.class);
  private static final EnumSet<ContractStatus> TERMINABLE_FROM =
      EnumSet.of(ContractStatus.ACTIVE, ContractStatus.SUSPENDED);

  private final ContractRepository contracts;
  private final ApplicationEventPublisher events;

  public TerminateContractService(ContractRepository contracts, ApplicationEventPublisher events) {
    this.contracts = contracts;
    this.events = events;
  }

  @Transactional
  public void terminate(
      ContractId id,
      LocalDate terminationDate,
      String reason,
      boolean earlyTermination,
      UserId by) {
    Contract contract =
        contracts.findById(id.value()).orElseThrow(() -> new ContractNotFoundException(id));

    if (!TERMINABLE_FROM.contains(contract.getStatus())) {
      throw new InvalidContractTransitionException(
          contract.getStatus(),
          earlyTermination ? ContractStatus.TERMINATED_EARLY : ContractStatus.EXPIRED);
    }

    contract.terminate(by, terminationDate, reason, earlyTermination);
    contracts.save(contract);

    BigDecimal cancellationFee =
        earlyTermination && contract.getCancellationFeeValue() != null
            ? contract.getCancellationFeeValue()
            : BigDecimal.ZERO;

    events.publishEvent(
        new ContractTerminatedEvent(
            contract.typedId(),
            currentTenantId(),
            terminationDate,
            reason,
            cancellationFee,
            earlyTermination,
            by,
            Instant.now()));

    log.info(
        "Contrato encerrado contractId={} number={} status={} terminationDate={} early={} fee={} terminatedBy={}",
        contract.typedId(),
        contract.getNumber(),
        contract.getStatus(),
        terminationDate,
        earlyTermination,
        cancellationFee,
        by);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
