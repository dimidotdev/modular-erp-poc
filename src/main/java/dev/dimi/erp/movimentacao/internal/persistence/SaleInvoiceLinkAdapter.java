package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleInvoiceLink;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de {@link SaleInvoiceLink}: aplica {@code Sale.attachInvoice} dentro da transação ativa
 * do caller (fiscal). {@link Propagation#MANDATORY} garante que escrita só ocorre se a tx do caller
 * estiver viva — falha imediata e visível caso contrário.
 */
@Slf4j
@Component
@RequiredArgsConstructor
class SaleInvoiceLinkAdapter implements SaleInvoiceLink {

  private final SaleRepository sales;

  @Override
  @Transactional(propagation = Propagation.MANDATORY)
  public void linkInvoice(SaleId saleId, UUID invoiceId) {
    sales
        .findById(saleId.value())
        .ifPresentOrElse(
            sale -> {
              sale.attachInvoice(invoiceId);
              sales.save(sale);
            },
            () ->
                log.warn(
                    "sale-invoice-link no-op — sale={} not found for invoice={}",
                    saleId.value(),
                    invoiceId));
  }
}
