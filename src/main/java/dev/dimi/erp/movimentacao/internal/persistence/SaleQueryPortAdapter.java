package dev.dimi.erp.movimentacao.internal.persistence;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.movimentacao.SaleForInvoicingView;
import dev.dimi.erp.movimentacao.SaleId;
import dev.dimi.erp.movimentacao.SaleQueryPort;
import dev.dimi.erp.movimentacao.SaleStatus;
import dev.dimi.erp.movimentacao.internal.domain.Sale;
import dev.dimi.erp.movimentacao.internal.domain.SaleItem;
import dev.dimi.erp.produto.PackagingId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de {@link SaleQueryPort}: carrega {@code Sale} fechada + {@code SaleItem}s e projeta em
 * {@link SaleForInvoicingView}. Read-only — não há mutação de venda no fluxo de emissão fiscal.
 */
@Component
@RequiredArgsConstructor
class SaleQueryPortAdapter implements SaleQueryPort {

  private final SaleRepository sales;
  private final SaleItemRepository items;

  @Override
  @Transactional(readOnly = true)
  public Optional<SaleForInvoicingView> findForInvoicing(SaleId id) {
    return sales
        .findById(id.value())
        .filter(s -> s.getStatus() == SaleStatus.CLOSED)
        .map(this::toView);
  }

  private SaleForInvoicingView toView(Sale sale) {
    List<SaleForInvoicingView.Item> projectedItems =
        items.findBySaleIdOrderByLineNumberAsc(sale.getId()).stream()
            .map(this::toItemView)
            .toList();
    return new SaleForInvoicingView(
        new SaleId(sale.getId()),
        new BranchId(sale.getBranchId()),
        new PersonId(sale.getCustomerId()),
        sale.getNumber(),
        sale.getStatus(),
        sale.getTotalGross(),
        sale.getTotalDiscount(),
        sale.getTotalFreight(),
        sale.getTotal(),
        projectedItems);
  }

  private SaleForInvoicingView.Item toItemView(SaleItem item) {
    return new SaleForInvoicingView.Item(
        item.getLineNumber(),
        new PackagingId(item.getPackagingId()),
        item.getQuantity(),
        item.getUnitPrice(),
        item.getDiscountValue(),
        item.getNetValue());
  }
}
