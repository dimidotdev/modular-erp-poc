package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.contratos.BillingCycleId;
import dev.dimi.erp.contratos.BillingCycleNotFoundException;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.ContractNotFoundException;
import dev.dimi.erp.contratos.ContractStatus;
import dev.dimi.erp.contratos.internal.domain.Adjustment;
import dev.dimi.erp.contratos.internal.domain.Amendment;
import dev.dimi.erp.contratos.internal.domain.BillingCycle;
import dev.dimi.erp.contratos.internal.domain.Contract;
import dev.dimi.erp.contratos.internal.domain.ContractItem;
import dev.dimi.erp.contratos.internal.persistence.AdjustmentRepository;
import dev.dimi.erp.contratos.internal.persistence.AmendmentRepository;
import dev.dimi.erp.contratos.internal.persistence.BillingCycleRepository;
import dev.dimi.erp.contratos.internal.persistence.ContractItemRepository;
import dev.dimi.erp.contratos.internal.persistence.ContractRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queries sobre contratos — leitura. Usado pelos controllers. Agrupa lookups sobre itens, aditivos,
 * reajustes e ciclos de faturamento para evitar proliferar services de leitura (mesmo padrão do
 * {@code ServiceOrderQueryService}).
 */
@Service
public class ContractQueryService {

  private final ContractRepository contracts;
  private final ContractItemRepository items;
  private final AmendmentRepository amendments;
  private final AdjustmentRepository adjustments;
  private final BillingCycleRepository cycles;

  public ContractQueryService(
      ContractRepository contracts,
      ContractItemRepository items,
      AmendmentRepository amendments,
      AdjustmentRepository adjustments,
      BillingCycleRepository cycles) {
    this.contracts = contracts;
    this.items = items;
    this.amendments = amendments;
    this.adjustments = adjustments;
    this.cycles = cycles;
  }

  @Transactional(readOnly = true)
  public Contract findById(ContractId id) {
    return contracts.findById(id.value()).orElseThrow(() -> new ContractNotFoundException(id));
  }

  @Transactional(readOnly = true)
  public Page<Contract> listByBranch(BranchId branchId, Pageable pageable) {
    return contracts.findByBranchId(branchId.value(), pageable);
  }

  @Transactional(readOnly = true)
  public Page<Contract> listByBranchAndStatus(
      BranchId branchId, ContractStatus status, Pageable pageable) {
    return contracts.findByStatusAndBranchId(status, branchId.value(), pageable);
  }

  @Transactional(readOnly = true)
  public List<ContractItem> findItems(ContractId id) {
    return items.findByContractIdOrderByLineNumberAsc(id.value());
  }

  @Transactional(readOnly = true)
  public List<Amendment> findAmendments(ContractId id) {
    return amendments.findByContractIdOrderByCreatedAtDesc(id.value());
  }

  @Transactional(readOnly = true)
  public List<Adjustment> findAdjustments(ContractId id) {
    return adjustments.findByContractIdOrderByAppliedOnDesc(id.value());
  }

  /**
   * Lista ciclos de faturamento do contrato, ordenados do mais recente para o mais antigo —
   * ordenação alinha com o padrão de aditivos e reajustes deste service. Lista crua sem paginação:
   * contratos de longa duração com recorrência {@code BIWEEKLY} podem acumular muitos ciclos;
   * paginação cursor entra em refinement futuro se necessário.
   */
  @Transactional(readOnly = true)
  public List<BillingCycle> findCycles(ContractId id) {
    return cycles.findByContractIdOrderByCompetenceMonthDesc(id.value());
  }

  @Transactional(readOnly = true)
  public BillingCycle findCycle(BillingCycleId id) {
    return cycles.findById(id.value()).orElseThrow(() -> new BillingCycleNotFoundException(id));
  }
}
