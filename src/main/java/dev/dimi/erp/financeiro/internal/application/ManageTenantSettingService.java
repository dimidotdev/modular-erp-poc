package dev.dimi.erp.financeiro.internal.application;

import dev.dimi.erp.financeiro.internal.domain.TenantSetting;
import dev.dimi.erp.financeiro.internal.persistence.TenantSettingRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lê e atualiza a {@link TenantSetting} (singleton por tenant). Consumido pelo controller {@code
 * /api/v1/financeiro/settings} e pelos listeners cross-modulo do financeiro.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManageTenantSettingService {

  private final TenantSettingRepository repository;

  /**
   * NÃO usa {@code readOnly=true} porque {@link #loadOrCreatePending()} dispara INSERT do singleton
   * na primeira chamada. Self-invocation não atravessa o proxy do Spring, então a anotação do
   * método interno é ignorada e a tx herdada do caller seria readOnly, causando "cannot execute
   * INSERT in a read-only transaction".
   */
  @Transactional
  public TenantSettingSummary get() {
    return TenantSettingSummary.of(loadOrCreatePending());
  }

  @Transactional
  public TenantSettingSummary update(UpdateTenantSettingCommand cmd) {
    TenantSetting s = loadOrCreatePending();
    s.updateDefaults(
        cmd.defaultRevenueChartAccountId() == null
            ? null
            : cmd.defaultRevenueChartAccountId().value(),
        cmd.defaultExpenseChartAccountId() == null
            ? null
            : cmd.defaultExpenseChartAccountId().value(),
        cmd.defaultCostCenterId() == null ? null : cmd.defaultCostCenterId().value(),
        cmd.actor() == null ? null : cmd.actor().value());
    TenantSetting saved = repository.save(s);
    log.info(
        "tenant-setting updated revenue={} expense={} cc={} actor={}",
        saved.getDefaultRevenueChartAccountId(),
        saved.getDefaultExpenseChartAccountId(),
        saved.getDefaultCostCenterId(),
        cmd.actor() == null ? null : cmd.actor().value());
    return TenantSettingSummary.of(saved);
  }

  /** Helper público de pacote para listeners — retorna o setting singleton ou cria vazio. */
  @Transactional
  public TenantSetting loadOrCreatePending() {
    return repository
        .findSingleton()
        .orElseGet(() -> repository.saveAndFlush(TenantSetting.initial()));
  }
}
