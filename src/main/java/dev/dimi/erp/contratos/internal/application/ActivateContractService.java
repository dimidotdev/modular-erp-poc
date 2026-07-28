package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.ContractActivatedEvent;
import dev.dimi.erp.contratos.ContractActivationValidationException;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractNotFoundException;
import dev.dimi.erp.contratos.internal.domain.Contract;
import dev.dimi.erp.contratos.internal.persistence.ContractItemRepository;
import dev.dimi.erp.contratos.internal.persistence.ContractRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ativa contrato {@code DRAFT → ACTIVE}. RN-CTR-002: contrato precisa ter ao menos 1 item, {@code
 * baseValue > 0} e {@code paymentConditionId} definido — caso contrário {@link
 * ContractActivationValidationException}. Transições inválidas (já ativo, suspenso, etc) sobem como
 * {@code InvalidContractTransitionException} via {@link Contract#activate(UserId)}.
 */
@Service
public class ActivateContractService {

  private static final Logger log = LoggerFactory.getLogger(ActivateContractService.class);

  private final ContractRepository contracts;
  private final ContractItemRepository items;
  private final ApplicationEventPublisher events;

  public ActivateContractService(
      ContractRepository contracts,
      ContractItemRepository items,
      ApplicationEventPublisher events) {
    this.contracts = contracts;
    this.items = items;
    this.events = events;
  }

  @Transactional
  public void activate(ContractId id, UserId activatedBy) {
    Contract contract =
        contracts.findById(id.value()).orElseThrow(() -> new ContractNotFoundException(id));

    int itemCount = items.findByContractIdOrderByLineNumberAsc(id.value()).size();
    if (itemCount == 0) {
      throw new ContractActivationValidationException("contrato sem itens");
    }
    if (contract.getBaseValue() == null
        || contract.getBaseValue().compareTo(BigDecimal.ZERO) <= 0) {
      throw new ContractActivationValidationException("base_value deve ser positivo");
    }
    if (contract.getPaymentConditionId() == null) {
      throw new ContractActivationValidationException("condição de pagamento obrigatória");
    }

    contract.activate(activatedBy);
    contracts.save(contract);

    events.publishEvent(
        new ContractActivatedEvent(
            contract.typedId(),
            currentTenantId(),
            contract.getStartDate(),
            activatedBy,
            Instant.now()));

    log.info(
        "Contrato ativado contractId={} number={} branchId={} activatedBy={}",
        contract.typedId(),
        contract.getNumber(),
        contract.branchIdTyped(),
        activatedBy);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
