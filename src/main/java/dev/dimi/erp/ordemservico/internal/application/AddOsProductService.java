package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.OsProductAddedEvent;
import dev.dimi.erp.ordemservico.OsProductId;
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
import dev.dimi.erp.produto.PackagingId;
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
 * Adiciona linha de produto a uma OS aberta. Não toca estoque — consumo OUTBOUND fica em {@link
 * ConsumeOsProductService}. Recomputa totais da OS após adicionar.
 *
 * <p>Bloqueia em status terminal (CLOSED, BILLED) — uma vez fechada/faturada, OS é read-only do
 * ponto de vista de itens.
 */
@Service
public class AddOsProductService {

  private static final Logger log = LoggerFactory.getLogger(AddOsProductService.class);
  private static final Set<String> TERMINAL_STATUSES = Set.of("CLOSED", "BILLED");

  private final ServiceOrderRepository orders;
  private final OsProductRepository products;
  private final OsServiceRepository services;
  private final WorkflowStatusRepository statuses;
  private final ApplicationEventPublisher events;

  public AddOsProductService(
      ServiceOrderRepository orders,
      OsProductRepository products,
      OsServiceRepository services,
      WorkflowStatusRepository statuses,
      ApplicationEventPublisher events) {
    this.orders = orders;
    this.products = products;
    this.services = services;
    this.statuses = statuses;
    this.events = events;
  }

  @Transactional
  public OsProductId addProduct(
      ServiceOrderId osId,
      PackagingId packagingId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue,
      UserId actor) {
    ServiceOrder os =
        orders.findById(osId.value()).orElseThrow(() -> new ServiceOrderNotFoundException(osId));

    String currentStatusCode = statusCodeOf(os);
    if (TERMINAL_STATUSES.contains(currentStatusCode)) {
      throw new ServiceOrderNotEditableException(osId, currentStatusCode);
    }

    int nextLine = products.maxLineByOs(os.getId()) + 1;

    OsProduct line =
        OsProduct.create(os.typedId(), nextLine, packagingId, quantity, unitPrice, discountValue);
    OsProduct saved = products.saveAndFlush(line);

    recomputeOsTotals(os);

    events.publishEvent(
        new OsProductAddedEvent(
            saved.typedId(),
            os.typedId(),
            currentTenantId(),
            saved.packagingIdTyped(),
            saved.getQuantity(),
            saved.getNetValue(),
            actor,
            Instant.now()));

    log.info(
        "OS produto adicionado osId={} line={} packagingId={} netValue={} actor={}",
        os.typedId(),
        nextLine,
        packagingId,
        saved.getNetValue(),
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
