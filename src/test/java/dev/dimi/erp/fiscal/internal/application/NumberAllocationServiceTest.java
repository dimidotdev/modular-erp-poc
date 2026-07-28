package dev.dimi.erp.fiscal.internal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.fiscal.FiscalModel;
import dev.dimi.erp.fiscal.SeriesExhaustedException;
import dev.dimi.erp.fiscal.SeriesNotAvailableException;
import dev.dimi.erp.fiscal.internal.domain.InvoiceSeries;
import dev.dimi.erp.fiscal.internal.persistence.InvoiceSeriesRepository;
import java.lang.reflect.Field;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NumberAllocationServiceTest {

  private static final BranchId BRANCH = new BranchId(UUID.randomUUID());

  @Mock private InvoiceSeriesRepository repository;
  @InjectMocks private NumberAllocationService service;

  @Test
  void allocatesAndIncrementsActiveSeries() {
    InvoiceSeries series = activeSeries(BRANCH.value(), FiscalModel.NFE, "1", 42L);
    when(repository.findForAllocation(BRANCH.value(), FiscalModel.NFE, "1"))
        .thenReturn(Optional.of(series));

    long allocated = service.allocate(BRANCH, FiscalModel.NFE, "1");

    assertThat(allocated).isEqualTo(42L);
    assertThat(series.getNextNumber()).isEqualTo(43L);
    verify(repository, times(1)).save(eq(series));
  }

  @Test
  void throwsWhenSeriesMissing() {
    when(repository.findForAllocation(any(), any(), any())).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.allocate(BRANCH, FiscalModel.NFE, "1"))
        .isInstanceOf(SeriesNotAvailableException.class);
  }

  @Test
  void throwsWhenSeriesInactive() {
    InvoiceSeries series = activeSeries(BRANCH.value(), FiscalModel.NFE, "1", 1L);
    setField(series, "isActive", false);
    when(repository.findForAllocation(BRANCH.value(), FiscalModel.NFE, "1"))
        .thenReturn(Optional.of(series));

    assertThatThrownBy(() -> service.allocate(BRANCH, FiscalModel.NFE, "1"))
        .isInstanceOf(SeriesNotAvailableException.class);
  }

  @Test
  void throwsWhenSeriesExhausted() {
    InvoiceSeries series =
        activeSeries(BRANCH.value(), FiscalModel.NFE, "1", NumberAllocationService.LEGAL_LIMIT + 1);
    when(repository.findForAllocation(BRANCH.value(), FiscalModel.NFE, "1"))
        .thenReturn(Optional.of(series));

    assertThatThrownBy(() -> service.allocate(BRANCH, FiscalModel.NFE, "1"))
        .isInstanceOf(SeriesExhaustedException.class);
  }

  private InvoiceSeries activeSeries(UUID branchId, FiscalModel model, String code, long next) {
    InvoiceSeries s = InvoiceSeries.create(branchId, model, code);
    setField(s, "nextNumber", next);
    return s;
  }

  private static void setField(Object target, String name, Object value) {
    try {
      Field f = target.getClass().getDeclaredField(name);
      f.setAccessible(true);
      f.set(target, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
