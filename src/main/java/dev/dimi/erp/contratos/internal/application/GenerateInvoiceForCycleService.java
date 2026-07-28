package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.BillingCycleId;
import dev.dimi.erp.contratos.BillingCycleNotFoundException;
import dev.dimi.erp.contratos.BillingCycleStatus;
import dev.dimi.erp.contratos.ContractCycleFailedEvent;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractInvoiceGeneratedEvent;
import dev.dimi.erp.contratos.ContractNotFoundException;
import dev.dimi.erp.contratos.ContractStatus;
import dev.dimi.erp.contratos.internal.domain.BillingCycle;
import dev.dimi.erp.contratos.internal.domain.Contract;
import dev.dimi.erp.contratos.internal.persistence.BillingCycleRepository;
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
 * Geração de invoice para um ciclo de faturamento. Slice 10.2: publica {@link
 * ContractInvoiceGeneratedEvent} para o {@code financeiro} criar o MVF a receber via listener — não
 * emite NFSe (refinement 10.3+ via {@code tenant_setting.contract_emit_nfse=true}).
 *
 * <p>Idempotente: ciclo já {@code GENERATED} retorna sem efeito; ciclo {@code FAILED} é resetado
 * antes da geração para permitir nova tentativa (re-uso pelo {@code
 * RegenerateBillingCycleService}). Ciclos {@code SKIPPED} também caem no {@code
 * resetForRegeneration} path? Não — domain só permite reset a partir de {@code FAILED}; outros
 * estados sobem {@code IllegalStateException} da entidade.
 *
 * <p>Se o contrato não estiver mais {@code ACTIVE} no momento da execução (suspenso/encerrado entre
 * o scheduling e a geração), o ciclo é marcado {@code SKIPPED} com motivo registrado em {@code
 * notes} — sem evento de invoice e sem falha. Para outras exceções, ciclo vira {@code FAILED} e
 * evento {@link ContractCycleFailedEvent} é publicado. O re-throw garante rollback transacional; o
 * status {@code FAILED} é persistido por uma sub-transação separada antes do throw via {@code
 * saveAndFlush} dentro do catch? Não — saveAndFlush dentro do catch numa única tx é descartado pelo
 * rollback. Em 10.2 aceitamos esse comportamento: cycle volta a {@code PENDING} no rollback e o
 * próximo run do job tenta de novo. Para persistência robusta de {@code FAILED}, 10.3 pode mover
 * esse markFailed para uma nova tx via {@code TransactionTemplate.PROPAGATION_REQUIRES_NEW}.
 */
@Service
public class GenerateInvoiceForCycleService {

  private static final Logger log = LoggerFactory.getLogger(GenerateInvoiceForCycleService.class);

  private final ContractRepository contracts;
  private final BillingCycleRepository cycles;
  private final ApplicationEventPublisher events;

  public GenerateInvoiceForCycleService(
      ContractRepository contracts,
      BillingCycleRepository cycles,
      ApplicationEventPublisher events) {
    this.contracts = contracts;
    this.cycles = cycles;
    this.events = events;
  }

  /**
   * Gera invoice (MVF a receber via listener financeiro) para um ciclo {@code PENDING} ou {@code
   * FAILED}. Idempotente: ciclo {@code GENERATED} retorna sem efeito.
   *
   * @param triggeredBy usuário que disparou (pode ser {@code null} quando origem é o job batch).
   */
  @Transactional
  public void generateForCycle(BillingCycleId cycleId, UserId triggeredBy) {
    BillingCycle cycle =
        cycles
            .findById(cycleId.value())
            .orElseThrow(() -> new BillingCycleNotFoundException(cycleId));

    if (cycle.getStatus() == BillingCycleStatus.GENERATED) {
      log.debug("Cycle {} already generated, skipping", cycleId.value());
      return;
    }

    if (cycle.getStatus() == BillingCycleStatus.FAILED) {
      cycle.resetForRegeneration();
    }

    Contract contract =
        contracts
            .findById(cycle.getContractId())
            .orElseThrow(
                () -> new ContractNotFoundException(new ContractId(cycle.getContractId())));

    if (contract.getStatus() != ContractStatus.ACTIVE) {
      cycle.markSkipped(
          "contrato não ACTIVE no momento da geração (status=" + contract.getStatus() + ")");
      cycles.saveAndFlush(cycle);
      log.info(
          "Cycle {} skipped: contract {} status={}",
          cycleId.value(),
          contract.typedId(),
          contract.getStatus());
      return;
    }

    try {
      // 10.2 NÃO emite NFSe — financeiro listener cria apenas MVF a receber via evento.
      // 10.3+ pode adicionar emissão fiscal via tenant_setting.contract_emit_nfse=true.

      // financial_record_id e invoice_id ficam null em 10.2; financeiro listener atualizará se
      // necessário em refinement futuro via QueryPort + update.
      cycle.markGenerated(Instant.now(), null, null);
      cycles.saveAndFlush(cycle);

      events.publishEvent(
          new ContractInvoiceGeneratedEvent(
              cycle.typedId(),
              contract.typedId(),
              currentTenantId(),
              contract.branchIdTyped(),
              contract.customerIdTyped(),
              contract.getContactId(),
              contract.getPaymentConditionId(),
              contract.getCostCenterId(),
              cycle.getCompetenceMonth(),
              cycle.getDueDate(),
              cycle.getValue(),
              contract.getTitle(),
              triggeredBy,
              Instant.now()));

      log.info(
          "Cycle {} GENERATED contract={} competence={} value={}",
          cycleId.value(),
          contract.typedId(),
          cycle.getCompetenceMonth(),
          cycle.getValue());
    } catch (RuntimeException e) {
      cycle.markFailed(e.getClass().getSimpleName() + ": " + e.getMessage());
      cycles.saveAndFlush(cycle);
      events.publishEvent(
          new ContractCycleFailedEvent(
              cycle.typedId(),
              contract.typedId(),
              currentTenantId(),
              cycle.getCompetenceMonth(),
              cycle.getFailureReason(),
              Instant.now()));
      log.warn("Cycle {} FAILED reason={}", cycleId.value(), cycle.getFailureReason());
      throw e; // rollback transaction; outbox re-publishes events
    }
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
