package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.estoque.MovementId;
import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.estoque.StockLocationId;
import dev.dimi.erp.estoque.StockQueryPort;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.OsItemNotFoundException;
import dev.dimi.erp.ordemservico.OsProductConsumedEvent;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.ServiceOrderNotFoundException;
import dev.dimi.erp.ordemservico.internal.domain.OsProduct;
import dev.dimi.erp.ordemservico.internal.domain.ServiceOrder;
import dev.dimi.erp.ordemservico.internal.persistence.OsProductRepository;
import dev.dimi.erp.ordemservico.internal.persistence.ServiceOrderRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consome (efetiva OUTBOUND no estoque) uma linha de produto previamente adicionada à OS.
 * Idempotente por {@code (osId, lineNumber)} via {@code idempotencyKey} no {@link
 * StockInventoryPort#ship} — segunda chamada do controller retorna o mesmo {@link MovementId}.
 * Domínio protege contra dupla marcação via {@code OsProductAlreadyConsumedException}.
 *
 * <p>Falhas técnicas do estoque ({@code InsufficientStockException} etc.) propagam — OS NÃO é
 * alterada (rollback da transação).
 */
@Service
public class ConsumeOsProductService {

  private static final Logger log = LoggerFactory.getLogger(ConsumeOsProductService.class);
  private static final String ORIGIN_TYPE = "OS";

  private final ServiceOrderRepository orders;
  private final OsProductRepository products;
  private final StockInventoryPort inventory;
  private final StockQueryPort stockQueries;
  private final ApplicationEventPublisher events;

  public ConsumeOsProductService(
      ServiceOrderRepository orders,
      OsProductRepository products,
      StockInventoryPort inventory,
      StockQueryPort stockQueries,
      ApplicationEventPublisher events) {
    this.orders = orders;
    this.products = products;
    this.inventory = inventory;
    this.stockQueries = stockQueries;
    this.events = events;
  }

  @Transactional
  public MovementId consume(ServiceOrderId osId, int lineNumber, UserId actor) {
    ServiceOrder os =
        orders.findById(osId.value()).orElseThrow(() -> new ServiceOrderNotFoundException(osId));

    OsProduct line =
        products
            .findByOsIdAndLineNumber(os.getId(), lineNumber)
            .orElseThrow(
                () -> new OsItemNotFoundException("product:line=" + lineNumber, osId.value()));

    if (line.getConsumedAt() != null) {
      // Antecipa OsProductAlreadyConsumedException via markConsumed — evita reserve/ship
      // desnecessários em retry pós-consumo. A exceção rica é mapeada para 422 no global handler.
      line.markConsumed(line.getStockMovementId());
    }

    StockLocationId location =
        stockQueries
            .findDefaultLocation(os.branchIdTyped())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Filial "
                            + os.branchIdTyped().value()
                            + " não possui local de estoque default configurado — "
                            + "consumo de OS exige location."));

    String idempotencyKey = "os:" + osId.value() + ":product:" + lineNumber;

    inventory.reserve(
        new StockInventoryPort.ReserveCommand(
            line.packagingIdTyped(),
            os.branchIdTyped(),
            location,
            null,
            line.getQuantity(),
            ORIGIN_TYPE,
            osId.value(),
            idempotencyKey,
            actor));

    MovementId movementId =
        inventory.ship(
            new StockInventoryPort.ShipCommand(
                line.packagingIdTyped(),
                os.branchIdTyped(),
                location,
                null,
                line.getQuantity(),
                ORIGIN_TYPE,
                osId.value(),
                idempotencyKey,
                actor));

    line.markConsumed(movementId.value());
    products.saveAndFlush(line);

    events.publishEvent(
        new OsProductConsumedEvent(
            line.typedId(),
            os.typedId(),
            currentTenantId(),
            line.packagingIdTyped(),
            line.getQuantity(),
            movementId.value(),
            actor,
            Instant.now()));

    log.info(
        "OS produto consumido osId={} line={} movementId={} actor={}",
        osId,
        lineNumber,
        movementId.value(),
        actor);

    return movementId;
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
