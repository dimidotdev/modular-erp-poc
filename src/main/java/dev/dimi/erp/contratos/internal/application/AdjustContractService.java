package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.AdjustmentId;
import dev.dimi.erp.contratos.AdjustmentIndex;
import dev.dimi.erp.contratos.ContractAdjustedEvent;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractNotFoundException;
import dev.dimi.erp.contratos.ContractStatus;
import dev.dimi.erp.contratos.InvalidAdjustmentPercentageException;
import dev.dimi.erp.contratos.InvalidContractTransitionException;
import dev.dimi.erp.contratos.internal.domain.Adjustment;
import dev.dimi.erp.contratos.internal.domain.Contract;
import dev.dimi.erp.contratos.internal.persistence.AdjustmentRepository;
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
 * Reajustes por índice (IGPM/IPCA/INPC/MANUAL) no contrato. Append-only: cada aplicação cria um
 * {@link Adjustment} novo (UNIQUE {@code (contract_id, applied_on)} previne duplicata na mesma
 * data) e atualiza {@code Contract.baseValue} + {@code lastAdjustmentDate} + {@code
 * nextAdjustmentDate} (= applied_on + 12 meses) na mesma transação.
 *
 * <p>Aceita apenas {@code ACTIVE/SUSPENDED} — em {@code DRAFT} a base é editada direto, e em
 * estados terminais ({@code EXPIRED/CANCELLED/TERMINATED_EARLY}) não há ciclo futuro a indexar.
 *
 * <p>Range {@code [-50, +500]} é guard-rail anti-typo, não regulatório.
 */
@Service
public class AdjustContractService {

  private static final Logger log = LoggerFactory.getLogger(AdjustContractService.class);
  private static final EnumSet<ContractStatus> ADJUSTABLE_FROM =
      EnumSet.of(ContractStatus.ACTIVE, ContractStatus.SUSPENDED);
  private static final BigDecimal MIN_PERCENTAGE = new BigDecimal("-50");
  private static final BigDecimal MAX_PERCENTAGE = new BigDecimal("500");

  private final ContractRepository contracts;
  private final AdjustmentRepository adjustments;
  private final ApplicationEventPublisher events;

  public AdjustContractService(
      ContractRepository contracts,
      AdjustmentRepository adjustments,
      ApplicationEventPublisher events) {
    this.contracts = contracts;
    this.adjustments = adjustments;
    this.events = events;
  }

  @Transactional
  public AdjustmentId apply(
      ContractId contractId,
      AdjustmentIndex index,
      BigDecimal percentage,
      LocalDate appliedOn,
      String notes,
      UserId appliedBy) {
    Contract contract =
        contracts
            .findById(contractId.value())
            .orElseThrow(() -> new ContractNotFoundException(contractId));

    if (!ADJUSTABLE_FROM.contains(contract.getStatus())) {
      // "to=ACTIVE" sinaliza o estado esperado para indexar (ACTIVE ou SUSPENDED são aceitos).
      throw new InvalidContractTransitionException(contract.getStatus(), ContractStatus.ACTIVE);
    }

    if (percentage.compareTo(MIN_PERCENTAGE) < 0 || percentage.compareTo(MAX_PERCENTAGE) > 0) {
      throw new InvalidAdjustmentPercentageException(percentage);
    }

    BigDecimal oldBaseValue = contract.getBaseValue();

    Adjustment adjustment =
        Adjustment.apply(contractId, appliedOn, index, percentage, oldBaseValue, notes, appliedBy);
    contract.applyAdjustment(percentage, appliedOn);

    adjustments.save(adjustment);
    contracts.save(contract);

    events.publishEvent(
        new ContractAdjustedEvent(
            adjustment.typedId(),
            contractId,
            currentTenantId(),
            index,
            percentage,
            adjustment.getOldBaseValue(),
            adjustment.getNewBaseValue(),
            appliedOn,
            appliedBy,
            Instant.now()));

    log.info(
        "Reajuste aplicado adjustmentId={} contractId={} index={} percentage={} oldBaseValue={} newBaseValue={} appliedOn={} appliedBy={}",
        adjustment.typedId(),
        contractId,
        index,
        percentage,
        adjustment.getOldBaseValue(),
        adjustment.getNewBaseValue(),
        appliedOn,
        appliedBy);

    return adjustment.typedId();
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
