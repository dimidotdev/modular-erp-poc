package dev.dimi.erp.fiscal.internal.application;

import dev.dimi.erp.fiscal.InvoiceId;
import dev.dimi.erp.fiscal.internal.domain.Invoice;
import dev.dimi.erp.fiscal.internal.persistence.InvoiceRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Consulta interna de {@link Invoice} por identificador. Listagens e filtros pertencem a serviços
 * de busca específicos.
 */
@Service
@RequiredArgsConstructor
public class InvoiceLookupService {

  private final InvoiceRepository invoices;

  @Transactional(readOnly = true)
  public Invoice findById(InvoiceId id) {
    return invoices
        .findById(id.value())
        .orElseThrow(() -> new EntityNotFoundException("Invoice " + id.value() + " não existe."));
  }
}
