package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.ServiceOrderNotFoundException;
import dev.dimi.erp.ordemservico.WorkflowStatusId;
import dev.dimi.erp.ordemservico.internal.domain.Billing;
import dev.dimi.erp.ordemservico.internal.domain.OsLabor;
import dev.dimi.erp.ordemservico.internal.domain.OsProduct;
import dev.dimi.erp.ordemservico.internal.domain.OsService;
import dev.dimi.erp.ordemservico.internal.domain.ServiceOrder;
import dev.dimi.erp.ordemservico.internal.domain.StatusHistory;
import dev.dimi.erp.ordemservico.internal.persistence.BillingRepository;
import dev.dimi.erp.ordemservico.internal.persistence.OsLaborRepository;
import dev.dimi.erp.ordemservico.internal.persistence.OsProductRepository;
import dev.dimi.erp.ordemservico.internal.persistence.OsServiceRepository;
import dev.dimi.erp.ordemservico.internal.persistence.ServiceOrderRepository;
import dev.dimi.erp.ordemservico.internal.persistence.StatusHistoryRepository;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Queries sobre OS — leitura. Usado pelos controllers. Slice 9.2a estende com queries sobre itens
 * filhos (produto, serviço, labor) — agrupados aqui para evitar proliferar services de leitura.
 */
@Service
public class ServiceOrderQueryService {

  private final ServiceOrderRepository orders;
  private final StatusHistoryRepository history;
  private final OsProductRepository products;
  private final OsServiceRepository services;
  private final OsLaborRepository labors;
  private final BillingRepository billings;

  public ServiceOrderQueryService(
      ServiceOrderRepository orders,
      StatusHistoryRepository history,
      OsProductRepository products,
      OsServiceRepository services,
      OsLaborRepository labors,
      BillingRepository billings) {
    this.orders = orders;
    this.history = history;
    this.products = products;
    this.services = services;
    this.labors = labors;
    this.billings = billings;
  }

  @Transactional(readOnly = true)
  public ServiceOrder findById(ServiceOrderId id) {
    return orders.findById(id.value()).orElseThrow(() -> new ServiceOrderNotFoundException(id));
  }

  @Transactional(readOnly = true)
  public Page<ServiceOrder> listByBranch(BranchId branchId, Pageable pageable) {
    return orders.findByBranchId(branchId.value(), pageable);
  }

  @Transactional(readOnly = true)
  public Page<ServiceOrder> listByBranchAndStatus(
      BranchId branchId, WorkflowStatusId statusId, Pageable pageable) {
    return orders.findByBranchIdAndStatusId(branchId.value(), statusId.value(), pageable);
  }

  @Transactional(readOnly = true)
  public Page<ServiceOrder> listByCustomer(PersonId customerId, Pageable pageable) {
    return orders.findByCustomerId(customerId.value(), pageable);
  }

  @Transactional(readOnly = true)
  public List<StatusHistory> historyOf(ServiceOrderId id) {
    return history.findByOsIdOrderByChangedAtAsc(id.value());
  }

  @Transactional(readOnly = true)
  public List<OsProduct> findProducts(ServiceOrderId id) {
    return products.findByOsIdOrderByLineNumberAsc(id.value());
  }

  @Transactional(readOnly = true)
  public List<OsService> findServices(ServiceOrderId id) {
    return services.findByOsIdOrderByLineNumberAsc(id.value());
  }

  @Transactional(readOnly = true)
  public List<OsLabor> findLabors(ServiceOrderId id) {
    return labors.findByOsIdOrderByStartedAtAsc(id.value());
  }

  /**
   * Faturamentos emitidos para a OS, ordenados por data de emissão ascendente. Slice 9.2b: lista
   * pode ter 0..N entradas — uma OS pode ter um {@code PARTIAL} (adiantamento) seguido de um {@code
   * FINAL}, ou só o {@code FINAL}, ou nenhum (OS cancelada antes de faturar).
   */
  @Transactional(readOnly = true)
  public List<Billing> findBillings(ServiceOrderId id) {
    return billings.findByOsIdOrderByIssuedAtAsc(id.value());
  }
}
