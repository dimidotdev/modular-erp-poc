package dev.dimi.erp.financeiro.internal.listener;

import dev.dimi.erp.compras.SupplierReturnIssuedEvent;
import dev.dimi.erp.financeiro.FinancialListenerSkippedEvent;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * v1: NÃO toca em MVF — só registra log + publica {@link FinancialListenerSkippedEvent} com código
 * {@code manual-reversal-required}. Estorno proporcional da MVF original é matemática não-trivial
 * (qual parcela? qual rateio?); fica para slice futuro quando regras forem confirmadas com cliente
 * real.
 *
 * <p>Operador resolve cancelando ou renegociando a MVF original via endpoints existentes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SupplierReturnIssuedFinancialListener {

  private final ApplicationEventPublisher events;

  @ApplicationModuleListener
  void on(SupplierReturnIssuedEvent event) {
    log.info(
        "supplier-return financial listener — devolução={} receipt={} total={} requer estorno manual de MVF",
        event.supplierReturnId().value(),
        event.receiptId().value(),
        event.total());
    events.publishEvent(
        new FinancialListenerSkippedEvent(
            event.tenantId(),
            "SupplierReturnIssuedEvent",
            event.supplierReturnId().value(),
            "manual-reversal-required",
            "Estorno proporcional automático fica para slice futuro — cancele/renegocie a MVF original manualmente.",
            Instant.now()));
  }
}
