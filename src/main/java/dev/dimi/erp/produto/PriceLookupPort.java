package dev.dimi.erp.produto;

import dev.dimi.erp.cadastros.BranchId;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

/**
 * Resolução de preço de venda. RN-PRD-004: preço autoritativo é o de uma {@code price_table_item}
 * ativa cobrindo (branch, packaging, date). Quando há sobreposição entre tabela específica de
 * filial e tabela global, a específica vence.
 *
 * <p>Consumidor (movimentação) decide o que fazer quando o resultado é {@code empty} — em geral
 * lançar {@code product.no-price} no fechamento da venda.
 */
public interface PriceLookupPort {

  /** Preço vigente para (branch, packaging, date). */
  Optional<PriceQuote> resolve(PackagingId packagingId, BranchId branchId, LocalDate when);

  /**
   * Preço de venda + desconto máximo permitido (fração 0..1) — RN-PRD-005 é enforced pelo caller em
   * movimentação.
   */
  record PriceQuote(
      PackagingId packagingId,
      PriceTableId priceTableId,
      BranchId branchId,
      BigDecimal salePrice,
      BigDecimal discountMax,
      LocalDate validFrom,
      LocalDate validUntil) {}
}
