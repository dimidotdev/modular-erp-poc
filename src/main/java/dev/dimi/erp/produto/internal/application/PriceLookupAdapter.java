package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.PriceLookupPort;
import dev.dimi.erp.produto.internal.domain.PriceTable;
import dev.dimi.erp.produto.internal.domain.PriceTableItem;
import dev.dimi.erp.produto.internal.persistence.PriceTableItemRepository;
import dev.dimi.erp.produto.internal.persistence.PriceTableRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementação do {@link PriceLookupPort}. RN-PRD-004: preço autoritativo é o item de uma tabela
 * ativa cobrindo {@code (branch, packaging, date)}.
 *
 * <p>Regra de preferência (mais específica vence): tabela específica de filial vence sobre tabela
 * global do tenant. {@link PriceTableRepository#findActiveOn} já devolve ordenado (filial primeiro
 * por {@code validFrom desc}), então pegamos o primeiro hit.
 */
@Component
@RequiredArgsConstructor
class PriceLookupAdapter implements PriceLookupPort {

  private final PriceTableRepository tables;
  private final PriceTableItemRepository items;

  @Override
  @Transactional(readOnly = true)
  public Optional<PriceQuote> resolve(PackagingId packagingId, BranchId branchId, LocalDate when) {
    List<PriceTable> candidates = tables.findActiveOn(branchId.value(), when);
    for (PriceTable t : candidates) {
      Optional<PriceTableItem> item =
          items.findByPriceTableIdAndPackagingId(t.id().value(), packagingId.value());
      if (item.isPresent()) {
        PriceTableItem i = item.get();
        return Optional.of(
            new PriceQuote(
                packagingId,
                t.id(),
                branchId,
                i.getSalePrice(),
                i.getDiscountMax(),
                t.getValidFrom(),
                t.getValidUntil()));
      }
    }
    return Optional.empty();
  }
}
