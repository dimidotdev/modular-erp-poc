package dev.dimi.erp.auditoria.internal.application;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job mensal que mantém a janela rolante de partições de {@code audit.audit_event} criando, no
 * primeiro dia de cada mês, as partições dos próximos 2 meses (corrente + 2 à frente). O drop de
 * partições antigas continua sob responsabilidade da retenção (chamada explícita a {@link
 * PartitionMaintenanceService#dropPartitionsBefore(java.time.LocalDate)} quando ativado em V1.x).
 *
 * <p>Cross-tenant pela mesma razão de {@link AuditRetentionJob}: schema {@code audit} é
 * compartilhado.
 *
 * <p>Concorrência distribuída via {@link SchedulerLock} para evitar que duas réplicas tentem o
 * mesmo {@code CREATE TABLE PARTITION OF} simultaneamente (é idempotente, mas o lock economiza
 * ruído de log e contenção em {@code pg_class}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PartitionMaintenanceJob {

  private final PartitionMaintenanceService partitionService;

  // Cria partições do mês +1 e +2 antecipadamente todo dia 1 às 02:00 BRT.
  @Scheduled(cron = "${erp-poc.auditoria.partition-job.cron:0 0 2 1 * *}")
  @SchedulerLock(
      name = "auditoria-partition-maintenance",
      lockAtMostFor = "PT30M",
      lockAtLeastFor = "PT1M")
  public void runMonthly() {
    log.info("PartitionMaintenanceJob start");
    try {
      int created = partitionService.ensurePartitionsAhead(2);
      log.info("PartitionMaintenanceJob done partsCreated={}", created);
    } catch (Exception e) {
      log.error("PartitionMaintenanceJob failed", e);
    }
  }
}
