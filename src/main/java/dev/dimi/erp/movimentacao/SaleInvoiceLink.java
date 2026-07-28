package dev.dimi.erp.movimentacao;

import java.util.UUID;

/**
 * Port público invocado pelo módulo fiscal para anexar o ID do {@code Invoice} autorizado ao {@code
 * Sale} de origem (atualiza {@code sale.invoice_id}).
 *
 * <p>A invocação é síncrona, dentro da mesma transação em que o fiscal marca o invoice como {@code
 * AUTHORIZED} — não há janela em que o invoice existe mas {@code sale.invoice_id} continue null.
 *
 * <p>Existir como port (e não como listener em movimentação consumindo {@code
 * InvoiceAuthorizedEvent}) é deliberado: permitiria ciclo {@code fiscal → movimentação → fiscal}
 * que o Spring Modulith rejeita. Eventos {@link dev.dimi.erp.fiscal.InvoiceAuthorizedEvent} seguem
 * disponíveis para {@code auditoria} e consumers futuros que não introduzam ciclos.
 */
public interface SaleInvoiceLink {

  /**
   * Atualiza {@code sale.invoice_id}. No-op se a venda não existe (warn em log) — o fiscal já tem
   * controle sobre o invoice; deixar a referência reversa sem efeito não bloqueia a emissão.
   *
   * @param saleId venda origem (mesmo {@code originId} usado no invoice)
   * @param invoiceId invoice autorizado
   */
  void linkInvoice(SaleId saleId, UUID invoiceId);
}
