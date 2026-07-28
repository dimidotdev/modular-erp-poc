package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.OsServiceAddedEvent;
import dev.dimi.erp.ordemservico.OsServiceId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.ServiceOrderNotEditableException;
import dev.dimi.erp.ordemservico.ServiceOrderNotFoundException;
import dev.dimi.erp.ordemservico.WorkflowStatusNotFoundException;
import dev.dimi.erp.ordemservico.internal.domain.OsProduct;
import dev.dimi.erp.ordemservico.internal.domain.OsService;
import dev.dimi.erp.ordemservico.internal.domain.ServiceOrder;
import dev.dimi.erp.ordemservico.internal.domain.WorkflowStatus;
import dev.dimi.erp.ordemservico.internal.persistence.OsProductRepository;
import dev.dimi.erp.ordemservico.internal.persistence.OsServiceRepository;
import dev.dimi.erp.ordemservico.internal.persistence.ServiceOrderRepository;
import dev.dimi.erp.ordemservico.internal.persistence.WorkflowStatusRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adiciona linha de serviço (LC 116) à OS aberta. Calcula ISSQN na criação ({@code netValue *
 * issqnRate / 100}). Bloqueia em status terminal (CLOSED, BILLED). Recomputa totais da OS.
 */
@Service
public class AddOsServiceService {

  private static final Logger log = LoggerFactory.getLogger(AddOsServiceService.class);
  private static final Set<String> TERMINAL_STATUSES = Set.of("CLOSED", "BILLED");

  private final ServiceOrderRepository orders;
  private final OsServiceRepository services;
  private final OsProductRepository products;
  private final WorkflowStatusRepository statuses;
  private final ApplicationEventPublisher events;

  public AddOsServiceService(
      ServiceOrderRepository orders,
      OsServiceRepository services,
      OsProductRepository products,
      WorkflowStatusRepository statuses,
      ApplicationEventPublisher events) {
    this.orders = orders;
    this.services = services;
    this.products = products;
    this.statuses = statuses;
    this.events = events;
  }

  @Transactional
  public OsServiceId addService(
      ServiceOrderId osId,
      String serviceCode,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue,
      BigDecimal issqnRate,
      UserId actor) {
    ServiceOrder os =
        orders.findById(osId.value()).orElseThrow(() -> new ServiceOrderNotFoundException(osId));

    String currentStatusCode = statusCodeOf(os);
    if (TERMINAL_STATUSES.contains(currentStatusCode)) {
      throw new ServiceOrderNotEditableException(osId, currentStatusCode);
    }

    int nextLine = services.maxLineByOs(os.getId()) + 1;

    OsService line =
        OsService.create(
            os.typedId(),
            nextLine,
            serviceCode,
            description,
            quantity,
            unitPrice,
            discountValue,
            issqnRate);
    OsService saved = services.saveAndFlush(line);

    recomputeOsTotals(os);

    events.publishEvent(
        new OsServiceAddedEvent(
            saved.typedId(),
            os.typedId(),
            currentTenantId(),
            saved.getServiceCode(),
            saved.getNetValue(),
            saved.getIssqnValue(),
            actor,
            Instant.now()));

    log.info(
        "OS serviço adicionado osId={} line={} serviceCode={} netValue={} issqnValue={} actor={}",
        os.typedId(),
        nextLine,
        serviceCode,
        saved.getNetValue(),
        saved.getIssqnValue(),
        actor);

    return saved.typedId();
  }

  private void recomputeOsTotals(ServiceOrder os) {
    List<OsProduct> allProducts = products.findByOsIdOrderByLineNumberAsc(os.getId());
    List<OsService> allServices = services.findByOsIdOrderByLineNumberAsc(os.getId());

    BigDecimal totalProducts =
        allProducts.stream().map(OsProduct::getNetValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalServices =
        allServices.stream().map(OsService::getNetValue).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal totalDiscount =
        allProducts.stream()
            .map(OsProduct::getDiscountValue)
            .reduce(BigDecimal.ZERO, BigDecimal::add)
            .add(
                allServices.stream()
                    .map(OsService::getDiscountValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add));

    os.recomputeTotals(totalProducts, totalServices, totalDiscount);
    orders.saveAndFlush(os);
  }

  private String statusCodeOf(ServiceOrder os) {
    WorkflowStatus status =
        statuses
            .findById(os.getStatusId())
            .orElseThrow(() -> new WorkflowStatusNotFoundException(os.getStatusId().toString()));
    return status.getCode();
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
