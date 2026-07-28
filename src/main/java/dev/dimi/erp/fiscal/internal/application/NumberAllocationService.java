package dev.dimi.erp.fiscal.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.fiscal.FiscalModel;
import dev.dimi.erp.fiscal.SeriesExhaustedException;
import dev.dimi.erp.fiscal.SeriesNotAvailableException;
import dev.dimi.erp.fiscal.internal.domain.InvoiceSeries;
import dev.dimi.erp.fiscal.internal.persistence.InvoiceSeriesRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * RN-FIS-002: aloca o próximo número de série sob lock pessimista (linha {@code invoice_series} via
 * {@link InvoiceSeriesRepository#findForAllocation}). Garante unicidade do {@code (branch_id,
 * model, series, number)} sob concorrência.
 *
 * <p>Roda em {@link Propagation#MANDATORY} — sempre dentro da transação de emissão, para que o
 * {@code SELECT FOR UPDATE} e o {@code UPDATE invoice_series} fiquem na mesma tx do {@code INSERT
 * invoice}. Se algo falhar depois, o número é liberado (rollback) — gaps são aceitos por
 * RN-FIS-002.
 *
 * <p>Limite legal SEFAZ: {@code 999_999_999}. Acima disso a série está esgotada e o operador deve
 * abrir nova série.
 */
@Service
@RequiredArgsConstructor
public class NumberAllocationService {

  static final long LEGAL_LIMIT = 999_999_999L;

  private final InvoiceSeriesRepository repository;

  /**
   * @return próximo número alocado para a (filial, modelo, série); incremento já persistido na
   *     mesma transação.
   * @throws SeriesNotAvailableException se a série não existe ou está desativada
   * @throws SeriesExhaustedException se o próximo número ultrapassaria {@value #LEGAL_LIMIT}
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public long allocate(BranchId branchId, FiscalModel model, String series) {
    InvoiceSeries row =
        repository
            .findForAllocation(branchId.value(), model, series)
            .filter(InvoiceSeries::isActive)
            .orElseThrow(
                () -> new SeriesNotAvailableException(branchId.value().toString(), model, series));

    if (row.getNextNumber() > LEGAL_LIMIT) {
      throw new SeriesExhaustedException(series, model);
    }

    long allocated = row.allocateNextNumber();
    repository.save(row);
    return allocated;
  }
}
