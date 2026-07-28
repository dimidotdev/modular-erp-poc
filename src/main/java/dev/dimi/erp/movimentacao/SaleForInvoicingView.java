package dev.dimi.erp.movimentacao;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot público de {@code Sale} fechada suficiente para o módulo fiscal montar um {@code
 * Invoice}. Contém o cabeçalho mínimo (filial, cliente, totais) e a lista de itens com referência a
 * {@link PackagingId} — o módulo fiscal resolve dados fiscais do SKU (NCM, origem) via {@code
 * produto}.
 *
 * <p>{@link SaleClosedEvent} carrega apenas dados resumidos; esta projeção fornece a lista
 * detalhada necessária à emissão fiscal. RN-FIS-009 garante imutabilidade após autorização, de modo
 * que os dados fiscais emitidos permanecem estáveis.
 */
public record SaleForInvoicingView(
    SaleId id,
    BranchId branchId,
    PersonId customerId,
    long number,
    SaleStatus status,
    BigDecimal totalGross,
    BigDecimal totalDiscount,
    BigDecimal totalFreight,
    BigDecimal total,
    List<Item> items) {

  /**
   * Linha de venda projetada para emissão fiscal. {@code packagingId} é obrigatório (não há itens
   * manuais em venda) — o módulo fiscal usa para resolver NCM/origem via {@code
   * PackagingQueryPort}.
   */
  public record Item(
      int lineNumber,
      PackagingId packagingId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discountValue,
      BigDecimal netValue) {}
}
