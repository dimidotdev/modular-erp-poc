package dev.dimi.erp.ordemservico;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.produto.PackagingId;
import java.math.BigDecimal;
import java.util.List;

/**
 * Snapshot público de uma OS pronta para faturamento, suficiente para o módulo fiscal montar tanto
 * o {@code InvoiceDraft} da NFe (linhas de produto) quanto o da NFSe (linhas de serviço) sem voltar
 * à OS via outros ports.
 *
 * <p>Análogo a {@code SaleForInvoicingView}, mas com dupla projeção: o módulo fiscal consome as
 * duas listas durante o faturamento simultâneo. {@code OsBilledEvent} carrega apenas totais;
 * portanto, as linhas detalhadas são fornecidas por esta projeção de consulta.
 */
public record OsForInvoicingView(
    ServiceOrderId osId,
    BranchId branchId,
    PersonId customerId,
    String customerCpfCnpj,
    String customerName,
    List<ProductLine> productLines,
    List<ServiceLine> serviceLines,
    BigDecimal productsAmount,
    BigDecimal servicesAmount,
    BigDecimal totalDiscount,
    BigDecimal total) {

  /**
   * Linha de produto consumido na OS, projetada para emissão da NFe. {@code packagingId} é
   * obrigatório — o módulo fiscal usa para resolver NCM/origem via {@code PackagingQueryPort}.
   */
  public record ProductLine(
      int lineNumber,
      PackagingId packagingId,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discount,
      BigDecimal netValue) {}

  /**
   * Linha de serviço prestado na OS, projetada para emissão da NFSe. {@code serviceCode} é o código
   * municipal LC 116; {@code issqnRate}/{@code issqnValue} já vêm calculados pelo módulo de OS para
   * compor o draft da NFSe sem nova consulta a tabelas tributárias.
   */
  public record ServiceLine(
      int lineNumber,
      String serviceCode,
      String description,
      BigDecimal quantity,
      BigDecimal unitPrice,
      BigDecimal discount,
      BigDecimal netValue,
      BigDecimal issqnRate,
      BigDecimal issqnValue) {}
}
