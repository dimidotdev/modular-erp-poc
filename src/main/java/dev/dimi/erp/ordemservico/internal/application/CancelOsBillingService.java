package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.estoque.MovementId;
import dev.dimi.erp.estoque.StockInventoryPort;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.BillingId;
import dev.dimi.erp.ordemservico.OsBillingAlreadyCancelledException;
import dev.dimi.erp.ordemservico.OsBillingCancelledEvent;
import dev.dimi.erp.ordemservico.OsBillingNotFoundException;
import dev.dimi.erp.ordemservico.internal.domain.Billing;
import dev.dimi.erp.ordemservico.internal.domain.OsProduct;
import dev.dimi.erp.ordemservico.internal.persistence.BillingRepository;
import dev.dimi.erp.ordemservico.internal.persistence.OsProductRepository;
import dev.dimi.erp.ordemservico.internal.persistence.ServiceOrderRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cancela um {@code service_order_billing} previamente emitido. Slice 9.2b cobre apenas o lado de
 * estoque + marcação local do billing — cancelamento real de NFe/NFSe na SEFAZ fica no slice 8.6
 * (cancelamento fiscal com step-up + janela de 24h SEFAZ). A invoice fica fisicamente autorizada
 * mas o billing é marcado como cancelado, e a OS continua em {@code BILLED} aguardando decisão
 * manual sobre transição.
 *
 * <p>Estorno de estoque: para cada {@link OsProduct} consumido (com {@code consumedAt} != null e
 * {@code stockMovementId} != null), chama {@link StockInventoryPort#reverse} criando movimento
 * REVERSAL. O ledger fica consistente; saldo on-hand volta. Idempotência fica a cargo do estoque
 * (RN-EST-009).
 *
 * <p>NÃO transita OS para CANCELLED automaticamente — cancelar billing não é cancelar OS. Se a
 * intenção for retomar a OS (refazer trabalho, refaturar), o caller usa endpoint próprio de
 * transição. Se for cancelar a OS por completo, idem.
 *
 * <p>TODO (slice 8.6): chamar {@code FiscalProvider.cancel(invoiceId, reason)} para NFe + NFSe
 * antes do estorno de estoque, registrar evento {@code InvoiceCancelledEvent} por documento e
 * persistir o protocolo de cancelamento. Por enquanto a NFe/NFSe permanece AUTHORIZED no provider —
 * aceitável em homologação; em produção exige slice 8.6 antes do go-live.
 */
@Service
public class CancelOsBillingService {

  private static final Logger log = LoggerFactory.getLogger(CancelOsBillingService.class);
  private static final String REVERSAL_ORIGIN = "OS_BILLING_CANCEL";

  private final BillingRepository billings;
  private final ServiceOrderRepository orders;
  private final OsProductRepository products;
  private final StockInventoryPort inventory;
  private final ApplicationEventPublisher events;

  public CancelOsBillingService(
      BillingRepository billings,
      ServiceOrderRepository orders,
      OsProductRepository products,
      StockInventoryPort inventory,
      ApplicationEventPublisher events) {
    this.billings = billings;
    this.orders = orders;
    this.products = products;
    this.inventory = inventory;
    this.events = events;
  }

  /**
   * Cancela o billing — estorna estoque + marca billing cancelado + publica evento. Falhas técnicas
   * propagam (rollback total).
   *
   * @param billingId billing a cancelar
   * @param reason motivo livre (para auditoria e marcação no billing)
   * @param actor usuário executor
   */
  @Transactional
  public void cancel(BillingId billingId, String reason, UserId actor) {
    // 1) carregar billing
    Billing billing =
        billings
            .findById(billingId.value())
            .orElseThrow(() -> new OsBillingNotFoundException(billingId));

    // 2) checar se já está cancelado (idempotência explícita)
    if (billing.getCancelledAt() != null) {
      log.warn(
          "cancel OS billing recusado — já cancelado billingId={} cancelledAt={}",
          billingId,
          billing.getCancelledAt());
      throw new OsBillingAlreadyCancelledException(billingId);
    }

    log.info(
        "cancel OS billing iniciando billingId={} osId={} reason={} actor={}",
        billingId,
        billing.getOsId(),
        reason,
        actor);

    // 3) garantir que a OS existe (sanidade — billing tem FK soft mas o repo é o source of truth)
    orders.findById(billing.getOsId()); // não usamos o resultado, só validamos existência

    // 4) estornar estoque dos produtos consumidos
    List<OsProduct> consumedProducts =
        products.findByOsIdOrderByLineNumberAsc(billing.getOsId()).stream()
            .filter(p -> p.getConsumedAt() != null && p.getStockMovementId() != null)
            .toList();

    for (OsProduct line : consumedProducts) {
      MovementId originalMovement = new MovementId(line.getStockMovementId());
      MovementId reversal =
          inventory.reverse(
              new StockInventoryPort.ReverseCommand(
                  originalMovement, REVERSAL_ORIGIN + ":billing=" + billingId.value(), actor));
      log.info(
          "cancel OS billing estoque estornado billingId={} line={} original={} reversal={}",
          billingId,
          line.getLineNumber(),
          originalMovement.value(),
          reversal.value());
    }

    // 5) marcar billing cancelado
    billing.markCancelled(reason, actor.value());
    billings.saveAndFlush(billing);

    // 6) publicar evento — auditoria + (futuro 8.6) fiscal pode escutar para cancelar NFe/NFSe
    events.publishEvent(
        new OsBillingCancelledEvent(
            billing.typedId(),
            billing.osIdTyped(),
            currentTenantId(),
            reason,
            actor,
            Instant.now()));

    log.info(
        "cancel OS billing concluído billingId={} reversões={} nfeInvoiceId={} nfseInvoiceId={}",
        billingId,
        consumedProducts.size(),
        billing.getNfeInvoiceId(),
        billing.getNfseInvoiceId());

    // 7) TODO slice 8.6: cancelar NFe via FiscalProvider.cancel(billing.nfeInvoiceId, reason) e
    // NFSe via FiscalProvider.cancel(billing.nfseInvoiceId, reason); persistir
    // InvoiceCancelledEvent por documento. Janela legal SEFAZ é 24h após autorização — UI deve
    // bloquear cancel além desse limite. Step-up auth obrigatório (RN-FIS-014).
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
