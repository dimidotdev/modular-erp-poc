package dev.dimi.erp.auditoria.internal.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job diário que dispara {@link AuditRetentionService#runRetention()} para aplicar as políticas
 * configuradas em {@code audit.retention_policy} (PURGE / ANONYMIZE por categoria).
 *
 * <p>Cross-tenant: roda fora de {@code TenantContext} porque o schema {@code audit} é compartilhado
 * por todos os tenants — a partição/categoria carregam o contexto na própria linha.
 *
 * <p>Concorrência distribuída garantida por {@link SchedulerLock}: em cluster com N réplicas,
 * apenas uma instância executa por janela; {@code lockAtMostFor} é teto de segurança caso a réplica
 * morra mid-run, {@code lockAtLeastFor} previne corrida em clocks levemente dessincronizados.
 *
 * <p>Falhas são logadas mas não relançadas — {@code @Scheduled} reagendará no próximo cron.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditRetentionJob {

  private final AuditRetentionService retentionService;

  @Scheduled(cron = "${erp-poc.auditoria.retention-job.cron:0 0 4 * * *}")
  @SchedulerLock(name = "auditoria-retention", lockAtMostFor = "PT2H", lockAtLeastFor = "PT1M")
  public void runDaily() {
    log.info("AuditRetentionJob start");
    try {
      var result = retentionService.runRetention();
      log.info(
          "AuditRetentionJob done purged={} anonymized={} partsDropped={} elapsed={}",
          result.purged(),
          result.anonymized(),
          result.partsDropped(),
          result.elapsed());
    } catch (Exception e) {
      log.error("AuditRetentionJob failed", e);
    }
  }
}
