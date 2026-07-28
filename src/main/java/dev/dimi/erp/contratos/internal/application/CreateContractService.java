package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.ContractCreatedEvent;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.internal.application.CreateContractCommand.ContractItemSpec;
import dev.dimi.erp.contratos.internal.domain.Contract;
import dev.dimi.erp.contratos.internal.domain.ContractItem;
import dev.dimi.erp.contratos.internal.domain.ContractItem.ItemType;
import dev.dimi.erp.contratos.internal.persistence.ContractItemRepository;
import dev.dimi.erp.contratos.internal.persistence.ContractRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cria contrato em {@code DRAFT} + itens. Sequencial por filial via {@link
 * ContractNumberSequencer}; race no UNIQUE {@code (branch_id, number)} dispara retry até {@value
 * #MAX_NUMBER_RETRIES} vezes (mesmo padrão do {@code OpenOsService}).
 *
 * <p>{@code baseValue} vem do command (campo negocial, não derivado). Apenas validamos contra a
 * soma dos {@code item.netValue} — discrepância maior que 1 centavo gera log de warning para o
 * operador conferir manualmente. Não falhamos, porque pode haver fee fixo/desconto contratual fora
 * dos itens — refino vira RN explícita em 10.2 se reclamarem.
 */
@Service
public class CreateContractService {

  private static final Logger log = LoggerFactory.getLogger(CreateContractService.class);
  private static final int MAX_NUMBER_RETRIES = 3;
  private static final BigDecimal TOLERANCE = new BigDecimal("0.01");

  private final ContractRepository contracts;
  private final ContractItemRepository items;
  private final ContractNumberSequencer sequencer;
  private final ApplicationEventPublisher events;

  public CreateContractService(
      ContractRepository contracts,
      ContractItemRepository items,
      ContractNumberSequencer sequencer,
      ApplicationEventPublisher events) {
    this.contracts = contracts;
    this.items = items;
    this.sequencer = sequencer;
    this.events = events;
  }

  @Transactional
  public ContractId create(CreateContractCommand cmd, UserId createdBy) {
    Contract saved = createWithRetry(cmd, createdBy);

    BigDecimal itemsTotal = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    int line = 1;
    for (ContractItemSpec spec : cmd.items()) {
      ContractItem item = toEntity(saved.typedId(), line++, spec);
      items.save(item);
      itemsTotal = itemsTotal.add(item.getNetValue());
    }

    BigDecimal diff = saved.getBaseValue().subtract(itemsTotal).abs();
    if (diff.compareTo(TOLERANCE) > 0) {
      log.warn(
          "contractId={} baseValue={} difere da soma dos itens={} (diff={}) — verificar fee/desconto fora dos itens",
          saved.typedId(),
          saved.getBaseValue(),
          itemsTotal,
          diff);
    }

    events.publishEvent(
        new ContractCreatedEvent(
            saved.typedId(),
            currentTenantId(),
            saved.branchIdTyped(),
            saved.customerIdTyped(),
            saved.getNumber(),
            saved.getBaseValue(),
            saved.getRecurrence(),
            saved.getBillingDay(),
            saved.getStartDate(),
            saved.getEndDate(),
            createdBy,
            Instant.now()));

    log.info(
        "Contrato criado contractId={} number={} branchId={} customerId={} items={} baseValue={} createdBy={}",
        saved.typedId(),
        saved.getNumber(),
        saved.branchIdTyped(),
        saved.customerIdTyped(),
        cmd.items().size(),
        saved.getBaseValue(),
        createdBy);

    return saved.typedId();
  }

  private Contract createWithRetry(CreateContractCommand cmd, UserId createdBy) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(cmd.branchId().value());
        Contract c =
            Contract.draft(
                number,
                cmd.branchId(),
                cmd.customerId(),
                cmd.contactId(),
                cmd.title(),
                cmd.description(),
                cmd.startDate(),
                cmd.endDate(),
                cmd.recurrence(),
                cmd.billingDay(),
                cmd.baseValue(),
                cmd.paymentConditionId(),
                cmd.costCenterId(),
                cmd.autoRenew(),
                cmd.renewalPeriodMonths(),
                cmd.cancellationClause(),
                cmd.cancellationFeeValue(),
                cmd.cancellationNoticeDays(),
                cmd.notes(),
                createdBy);
        return contracts.saveAndFlush(c);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.warn(
            "Contract number race attempt={} branchId={} — retentando",
            attempts,
            cmd.branchId().value());
      }
    }
  }

  private ContractItem toEntity(ContractId contractId, int line, ContractItemSpec spec) {
    if (spec.type() == ItemType.PRODUCT) {
      return ContractItem.product(
          contractId,
          line,
          new PackagingId(spec.packagingId()),
          spec.description(),
          spec.quantity(),
          spec.unitPrice(),
          spec.discountValue());
    }
    return ContractItem.service(
        contractId,
        line,
        spec.serviceCode(),
        spec.description(),
        spec.quantity(),
        spec.unitPrice(),
        spec.discountValue());
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
