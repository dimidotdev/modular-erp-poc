package dev.dimi.erp.fiscal;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.fiscal.internal.domain.TaxType;
import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot completo passado ao {@link FiscalProvider#authorize(InvoiceDraft)} para que o adapter
 * (in-memory em 8.2; ACBr em 8.3b) monte o XML, assine com o certificado A1 e submeta à SEFAZ.
 *
 * <p>O draft é montado pelo {@code IssueInvoiceFromSaleService} a partir de:
 *
 * <ul>
 *   <li>{@code BranchQueryPort.findById} para dados fiscais da filial (CNPJ, regime, ambiente).
 *   <li>{@code SaleQueryPort.findForInvoicing} para o cabeçalho e itens da venda.
 *   <li>{@code PersonQueryPort.findById} para nome legal + CPF/CNPJ do destinatário (8.3a).
 *   <li>{@code ProductQueryPort.findPackaging} para NCM/CEST/origem/SKU/descrição do item (8.3a).
 *   <li>{@code NumberAllocationService.allocate} para o próximo número da série.
 *   <li>{@code TaxCalculationService.compute} para os impostos por item (ICMS/PIS/COFINS) — 8.3a.
 * </ul>
 *
 * <p>O {@link #invoiceId} já é o ID persistido em {@code DRAFT}; o provider pode usá-lo como
 * correlation key em logs/retry. Idempotência por origem (RN-FIS-007) é garantida antes deste draft
 * chegar — se já houver invoice {@code AUTHORIZED} para a origem, o service retorna direto sem
 * chamar o provider.
 */
public record InvoiceDraft(
    InvoiceId invoiceId,
    BranchId branchId,
    String branchCnpj,
    FiscalModel model,
    String series,
    long number,
    NfeEnvironment environment,
    InvoiceOperation operation,
    ConsumerType consumerType,
    String customerCpfCnpj,
    String customerName,
    BigDecimal totalProducts,
    BigDecimal totalDiscount,
    BigDecimal totalFreight,
    BigDecimal totalTaxes,
    BigDecimal total,
    List<DraftItem> items) {

  /**
   * Linha do draft. Reflete {@code InvoiceItem} antes do invoice ser autorizado. {@link #taxes}
   * carrega o detalhamento por imposto (ICMS, PIS, COFINS) calculado em 8.3a — adapter in-memory
   * pode ignorar; adapter ACBr (8.3b) consome para montar as tags {@code <ICMS*>}/{@code <PIS>}/
   * {@code <COFINS>} do XML.
   */
  public record DraftItem(
      int lineNumber,
      String itemCode,
      String description,
      String ncm,
      String cfop,
      String origin,
      BigDecimal quantity,
      String unitCommercial,
      String unitFiscal,
      BigDecimal unitValue,
      BigDecimal grossValue,
      BigDecimal discount,
      BigDecimal netValue,
      List<DraftTax> taxes) {}

  /**
   * Detalhamento por imposto de uma {@link DraftItem}. Espelha {@code TaxCalculation} persistido,
   * carregado para o provider montar o XML.
   *
   * @param tax tipo de imposto
   * @param cstCsosn CST (3 dígitos com origem prefixada, ex {@code "000"}, {@code "020"}) para
   *     Lucro Real/Presumido; CSOSN (3 dígitos, ex {@code "102"}, {@code "500"}) para Simples
   *     Nacional. Para PIS/COFINS, código próprio (ex {@code "01"} = tributada com alíquota básica,
   *     {@code "99"} = outras operações no Simples).
   * @param base base de cálculo
   * @param rate alíquota em percentual (ex {@code 18.00} para 18%)
   * @param value valor calculado do imposto
   * @param baseSt base de cálculo de substituição tributária — geralmente {@code null} no v1
   *     (RN-FIS-006 cobre ST em slice futuro)
   * @param rateSt alíquota ST
   * @param valueSt valor ST
   */
  public record DraftTax(
      TaxType tax,
      String cstCsosn,
      BigDecimal base,
      BigDecimal rate,
      BigDecimal value,
      BigDecimal baseSt,
      BigDecimal rateSt,
      BigDecimal valueSt) {}
}
