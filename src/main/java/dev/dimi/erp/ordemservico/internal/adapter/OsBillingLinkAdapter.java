package dev.dimi.erp.ordemservico.internal.adapter;

import dev.dimi.erp.ordemservico.OsBilledEvent;
import dev.dimi.erp.ordemservico.OsBillingLink;
import dev.dimi.erp.ordemservico.OsBillingType;
import dev.dimi.erp.ordemservico.internal.application.ChangeStatusService;
import dev.dimi.erp.ordemservico.internal.domain.Billing;
import dev.dimi.erp.ordemservico.internal.domain.BillingType;
import dev.dimi.erp.ordemservico.internal.persistence.BillingRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de {@link OsBillingLink}: persiste {@link Billing} a partir do snapshot vindo do módulo
 * fiscal, transiciona a OS para {@code BILLED} via {@link ChangeStatusService} e publica {@link
 * OsBilledEvent} "gordinho" para o financeiro materializar a MVF.
 *
 * <p>Pacote {@code internal/adapter} — fora da API pública; quem usa é o módulo fiscal via
 * interface, sem visibilidade do componente concreto.
 *
 * <p>Idempotência: lookup por {@code idempotencyKey} antes do insert evita duplicar billing quando
 * o fiscal repete a chamada (mesmo {@code Idempotency-Key} no POST). O método é
 * {@code @Transactional} porque é o orquestrador desta etapa do faturamento; falha em qualquer
 * passo (persistência, transição, publicação) rolba tudo.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class OsBillingLinkAdapter implements OsBillingLink {

  private final BillingRepository billings;
  private final ChangeStatusService changeStatusService;
  private final ApplicationEventPublisher events;

  @Override
  @Transactional
  public RecordResult recordBillingInvoices(RecordCommand cmd) {
    if (cmd.idempotencyKey() != null && !cmd.idempotencyKey().isBlank()) {
      Optional<Billing> existing = billings.findByIdempotencyKey(cmd.idempotencyKey());
      if (existing.isPresent()) {
        log.debug(
            "OS billing idempotent hit key={} billingId={}",
            cmd.idempotencyKey(),
            existing.get().typedId());
        return RecordResult.existing(existing.get().typedId());
      }
    }

    // Persiste billing — domain factory valida que pelo menos um invoice id != null
    Billing saved =
        billings.saveAndFlush(
            Billing.issue(
                cmd.osId(),
                mapType(cmd.billingType()),
                cmd.nfeInvoiceId(),
                cmd.nfseInvoiceId(),
                cmd.productsAmount(),
                cmd.servicesAmount(),
                cmd.total(),
                cmd.notes(),
                cmd.issuedBy().value(),
                cmd.idempotencyKey()));

    // Transita OS p/ BILLED (validado pelo grafo de workflow)
    changeStatusService.transitionTo(
        cmd.osId(), "BILLED", "Faturada via " + saved.typedId(), cmd.issuedBy());

    // Publica evento gordinho — financeiro listener consome para criar MVF
    events.publishEvent(
        new OsBilledEvent(
            saved.typedId(),
            cmd.osId(),
            currentTenantId(),
            cmd.branchId(),
            cmd.customerId(),
            cmd.nfeInvoiceId(),
            cmd.nfseInvoiceId(),
            cmd.productsAmount(),
            cmd.servicesAmount(),
            cmd.total(),
            cmd.paymentConditionId(),
            cmd.issuedBy(),
            Instant.now()));

    log.info(
        "OS billing criado osId={} billingId={} nfe={} nfse={}",
        cmd.osId(),
        saved.typedId(),
        cmd.nfeInvoiceId(),
        cmd.nfseInvoiceId());

    return RecordResult.created(saved.typedId());
  }

  private static BillingType mapType(OsBillingType t) {
    return switch (t) {
      case PARTIAL -> BillingType.PARTIAL;
      case FINAL -> BillingType.FINAL;
    };
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
