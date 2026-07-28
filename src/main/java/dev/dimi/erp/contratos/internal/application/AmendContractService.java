package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.AmendmentId;
import dev.dimi.erp.contratos.AmendmentNotEditableException;
import dev.dimi.erp.contratos.AmendmentNotFoundException;
import dev.dimi.erp.contratos.AmendmentType;
import dev.dimi.erp.contratos.ContractAmendedEvent;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractNotEditableException;
import dev.dimi.erp.contratos.ContractNotFoundException;
import dev.dimi.erp.contratos.ContractStatus;
import dev.dimi.erp.contratos.internal.domain.Amendment;
import dev.dimi.erp.contratos.internal.domain.Contract;
import dev.dimi.erp.contratos.internal.persistence.AmendmentRepository;
import dev.dimi.erp.contratos.internal.persistence.ContractRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aditivos contratuais. {@code createAmendment} cria registro {@code DRAFT} sem mutar o contrato —
 * só faz sentido em contratos {@code ACTIVE/SUSPENDED} (em {@code DRAFT} a edição é direta, sem
 * aditivo). {@code effectivate} aplica o efeito do aditivo no contrato (extension, value, etc) e
 * publica {@link ContractAmendedEvent}.
 *
 * <p>{@code ITEM_ADD}/{@code ITEM_REMOVE} ficam para v1.1+ — payload JSON estruturado precisa de
 * desenho mais cuidadoso (mapear pra {@code ContractItem} + lógica de prorrateio); v1 lança {@link
 * UnsupportedOperationException} com mensagem clara orientando a editar em {@code DRAFT}.
 *
 * <p>Step-up para {@code effectivate} fica no controller via {@code @PreAuthorize}; {@code
 * createAmendment} é só rascunho e não exige.
 */
@Service
public class AmendContractService {

  private static final Logger log = LoggerFactory.getLogger(AmendContractService.class);

  private final ContractRepository contracts;
  private final AmendmentRepository amendments;
  private final ApplicationEventPublisher events;

  public AmendContractService(
      ContractRepository contracts,
      AmendmentRepository amendments,
      ApplicationEventPublisher events) {
    this.contracts = contracts;
    this.amendments = amendments;
    this.events = events;
  }

  @Transactional
  public AmendmentId createAmendment(
      ContractId contractId,
      AmendmentType type,
      LocalDate effectiveFrom,
      String description,
      LocalDate newEndDate,
      BigDecimal newBaseValue,
      String payload,
      UserId createdBy) {
    Contract contract =
        contracts
            .findById(contractId.value())
            .orElseThrow(() -> new ContractNotFoundException(contractId));

    if (contract.getStatus() == ContractStatus.DRAFT) {
      throw new ContractNotEditableException(contractId, contract.getStatus());
    }

    Amendment amendment =
        Amendment.draft(
            contractId,
            type,
            effectiveFrom,
            description,
            newEndDate,
            newBaseValue,
            payload,
            createdBy);
    amendments.save(amendment);

    log.info(
        "Aditivo criado (DRAFT) amendmentId={} contractId={} type={} effectiveFrom={} createdBy={}",
        amendment.typedId(),
        contractId,
        type,
        effectiveFrom,
        createdBy);

    return amendment.typedId();
  }

  @Transactional
  public void effectivate(AmendmentId amendmentId, UserId effectivatedBy) {
    Amendment amendment =
        amendments
            .findById(amendmentId.value())
            .orElseThrow(() -> new AmendmentNotFoundException(amendmentId));

    if (amendment.getStatus() != Amendment.AmendmentStatus.DRAFT) {
      throw new AmendmentNotEditableException(amendmentId, amendment.getStatus().name());
    }

    ContractId contractId = amendment.contractIdTyped();
    Contract contract =
        contracts
            .findById(contractId.value())
            .orElseThrow(() -> new ContractNotFoundException(contractId));

    switch (amendment.getType()) {
      case EXTENSION -> contract.setEndDate(amendment.getNewEndDate());
      case VALUE ->
          contract.updateBaseValue(amendment.getNewBaseValue(), amendment.getEffectiveFrom());
      case ITEM_ADD, ITEM_REMOVE ->
          throw new UnsupportedOperationException(
              "ITEM_ADD/ITEM_REMOVE via aditivo entra em v1.1+; adicione item manualmente em DRAFT.");
      case OTHER -> {
        // apenas registra description — nenhuma mutação no contrato
      }
    }

    amendment.effectivate();
    amendments.save(amendment);
    contracts.save(contract);

    events.publishEvent(
        new ContractAmendedEvent(
            amendment.typedId(),
            contractId,
            currentTenantId(),
            amendment.getType(),
            amendment.getEffectiveFrom(),
            amendment.getNewBaseValue(),
            amendment.getNewEndDate(),
            effectivatedBy,
            Instant.now()));

    log.info(
        "Aditivo efetivado amendmentId={} contractId={} type={} effectiveFrom={} effectivatedBy={}",
        amendment.typedId(),
        contractId,
        amendment.getType(),
        amendment.getEffectiveFrom(),
        effectivatedBy);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
