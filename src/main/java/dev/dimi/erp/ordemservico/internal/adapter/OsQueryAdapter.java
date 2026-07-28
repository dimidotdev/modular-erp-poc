package dev.dimi.erp.ordemservico.internal.adapter;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.PersonQueryPort;
import dev.dimi.erp.cadastros.PersonView;
import dev.dimi.erp.ordemservico.OsForInvoicingView;
import dev.dimi.erp.ordemservico.OsQueryPort;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.internal.domain.OsProduct;
import dev.dimi.erp.ordemservico.internal.domain.OsService;
import dev.dimi.erp.ordemservico.internal.domain.ServiceOrder;
import dev.dimi.erp.ordemservico.internal.persistence.OsProductRepository;
import dev.dimi.erp.ordemservico.internal.persistence.OsServiceRepository;
import dev.dimi.erp.ordemservico.internal.persistence.ServiceOrderRepository;
import dev.dimi.erp.produto.PackagingId;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter de {@link OsQueryPort}: carrega {@code ServiceOrder} + itens (produto e serviço) + dados
 * do customer via {@link PersonQueryPort} e projeta em {@link OsForInvoicingView}.
 *
 * <p>Análogo ao {@code SaleQueryPortAdapter} do módulo movimentação. Read-only — não há mutação de
 * OS no fluxo de emissão fiscal; quem dispara billing/transição de status é o orquestrador interno
 * que escuta o resultado do faturamento.
 *
 * <p>{@code customerCpfCnpj} e {@code customerName} vêm da projeção pública {@code PersonView}.
 * Pessoa anonimizada por LGPD (RN-CAD-011) retorna {@code document=null} — o módulo fiscal valida
 * antes de emitir e levanta {@code PersonAnonymizedForInvoice} se for o caso.
 */
@Component
@RequiredArgsConstructor
class OsQueryAdapter implements OsQueryPort {

  private final ServiceOrderRepository orders;
  private final OsProductRepository products;
  private final OsServiceRepository services;
  private final PersonQueryPort persons;

  @Override
  @Transactional(readOnly = true)
  public Optional<OsForInvoicingView> findForInvoicing(ServiceOrderId id) {
    return orders.findById(id.value()).map(this::toView);
  }

  private OsForInvoicingView toView(ServiceOrder os) {
    PersonId customerId = os.customerIdTyped();
    PersonView customer = persons.findById(customerId).orElse(null);
    String customerName = customer == null ? null : customer.legalName();
    String customerDocument = customer == null ? null : customer.document();

    List<OsForInvoicingView.ProductLine> productLines =
        products.findByOsIdOrderByLineNumberAsc(os.getId()).stream()
            .map(this::toProductLine)
            .toList();
    List<OsForInvoicingView.ServiceLine> serviceLines =
        services.findByOsIdOrderByLineNumberAsc(os.getId()).stream()
            .map(this::toServiceLine)
            .toList();

    return new OsForInvoicingView(
        os.typedId(),
        new BranchId(os.getBranchId()),
        customerId,
        customerDocument,
        customerName,
        productLines,
        serviceLines,
        os.getTotalProducts(),
        os.getTotalServices(),
        os.getTotalDiscount(),
        os.getTotal());
  }

  private OsForInvoicingView.ProductLine toProductLine(OsProduct p) {
    return new OsForInvoicingView.ProductLine(
        p.getLineNumber(),
        new PackagingId(p.getPackagingId()),
        p.getQuantity(),
        p.getUnitPrice(),
        p.getDiscountValue(),
        p.getNetValue());
  }

  private OsForInvoicingView.ServiceLine toServiceLine(OsService s) {
    return new OsForInvoicingView.ServiceLine(
        s.getLineNumber(),
        s.getServiceCode(),
        s.getDescription(),
        s.getQuantity(),
        s.getUnitPrice(),
        s.getDiscountValue(),
        s.getNetValue(),
        s.getIssqnRate(),
        s.getIssqnValue());
  }
}
