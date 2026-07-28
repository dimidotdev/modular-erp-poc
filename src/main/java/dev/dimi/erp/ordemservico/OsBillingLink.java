package dev.dimi.erp.ordemservico;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import java.math.BigDecimal;
import java.util.UUID;

/**
 * Port síncrona invocada pelo módulo fiscal APÓS emitir NFe e/ou NFSe a partir de uma OS, para
 * registrar o billing correspondente e transitar a OS para {@code BILLED}.
 *
 * <p>Assim como {@link dev.dimi.erp.movimentacao.SaleInvoiceLink}, mantém OS como dono dos dados de
 * billing sem que o módulo fiscal precise tocar em {@code service_order_billing}. Roda na mesma
 * transação do {@code IssueInvoiceFromOsService} — falha no link rolba tudo (incluindo as invoices
 * fiscais).
 *
 * <p>Idempotência: a implementação deve consultar billing por {@code idempotencyKey} e retornar o
 * billingId existente sem efeitos colaterais quando já houver.
 */
public interface OsBillingLink {

  RecordResult recordBillingInvoices(RecordCommand cmd);

  /** Snapshot enviado pelo fiscal após emitir as invoices. */
  record RecordCommand(
      ServiceOrderId osId,
      OsBillingType billingType,
      UUID nfeInvoiceId, // null se OS só tinha serviços
      UUID nfseInvoiceId, // null se OS só tinha produtos
      BigDecimal productsAmount,
      BigDecimal servicesAmount,
      BigDecimal totalDiscount,
      BigDecimal total,
      BranchId branchId, // pra evento gordinho do OsBilledEvent
      PersonId customerId,
      UUID paymentConditionId, // pode ser null
      String notes,
      UserId issuedBy,
      String idempotencyKey) {}

  /** Resultado do link — billing já criado e (re)transitado. */
  record RecordResult(BillingId billingId, boolean created) {

    public static RecordResult created(BillingId id) {
      return new RecordResult(id, true);
    }

    public static RecordResult existing(BillingId id) {
      return new RecordResult(id, false);
    }
  }
}
