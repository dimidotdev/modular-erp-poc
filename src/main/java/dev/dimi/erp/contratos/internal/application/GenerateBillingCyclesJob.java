package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.internal.domain.BillingCycle;
import dev.dimi.erp.contratos.internal.persistence.BillingCycleRepository;
import dev.dimi.erp.identidade.TenantQueryPort;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job diário que (a) materializa próximos ciclos {@code PENDING} para contratos {@code ACTIVE} via
 * {@link ScheduleBillingCyclesService} e (b) processa ciclos {@code PENDING} cuja {@code
 * competence_month} já está dentro da janela de execução (hoje + 7 dias), gerando MVF a receber via
 * {@link GenerateInvoiceForCycleService}.
 *
 * <p>Disparo: cron padrão {@code 0 0 3 * * *} (03:00 server time). Iteração cross-tenant via {@link
 * TenantQueryPort} + {@link TenantContext} (mesmo padrão de {@code CertificateExpiryJob}). Lock
 * distribuído via ShedLock — {@code lockAtMostFor=PT15M} cobre tenants pequenos a médios; revisar
 * quando passar de ~50 tenants ativos com muitos contratos.
 *
 * <p>Habilitado por padrão; desligar com {@code erp-poc.contratos.cycles-job.enabled=false} em
 * ambientes de teste ou em cluster passivo (failover ativo/passivo).
 */
@Component
@ConditionalOnProperty(
    name = "erp-poc.contratos.cycles-job.enabled",
    havingValue = "true",
    matchIfMissing = true)
public class GenerateBillingCyclesJob {

  private static final Logger log = LoggerFactory.getLogger(GenerateBillingCyclesJob.class);
  private static final ZoneId BRT = ZoneId.of("America/Sao_Paulo");
  private static final int PROCESS_WINDOW_DAYS = 7;

  private final TenantQueryPort tenants;
  private final ScheduleBillingCyclesService scheduleService;
  private final GenerateInvoiceForCycleService generateService;
  private final BillingCycleRepository cycles;

  public GenerateBillingCyclesJob(
      TenantQueryPort tenants,
      ScheduleBillingCyclesService scheduleService,
      GenerateInvoiceForCycleService generateService,
      BillingCycleRepository cycles) {
    this.tenants = tenants;
    this.scheduleService = scheduleService;
    this.generateService = generateService;
    this.cycles = cycles;
  }

  /**
   * Roda diariamente às 03:00 (server time, default BRT via cron). Lock distribuído via ShedLock.
   */
  @Scheduled(cron = "${erp-poc.contratos.cycles-job.cron:0 0 3 * * *}")
  @SchedulerLock(
      name = "contratos-generate-billing-cycles",
      lockAtMostFor = "PT15M",
      lockAtLeastFor = "PT1M")
  public void runDaily() {
    runManually(LocalDate.now(BRT));
  }

  /**
   * Executa o mesmo fluxo de {@link #runDaily()} para uma data arbitrária — itera tenants ativos,
   * agenda próximos ciclos via {@link ScheduleBillingCyclesService} e processa pendentes na janela
   * de execução. Sem {@code @SchedulerLock}: o caller (endpoint REST com permission {@code
   * contrato.faturar}, ou teste) é autoridade sobre concorrência. Útil para disparar o job
   * manualmente em dev/teste e para janelas de catch-up após indisponibilidade do cron.
   */
  public void runManually(LocalDate today) {
    LocalDate processUntil = today.plusDays(PROCESS_WINDOW_DAYS);
    log.info("GenerateBillingCyclesJob start today={}", today);

    int totalScheduled = 0;
    int totalGenerated = 0;
    int totalFailed = 0;

    List<Tenant> active = tenants.findAllActive();
    for (Tenant tenant : active) {
      try {
        TenantContext.set(tenant);
        int sched = scheduleService.scheduleUpcoming(today);
        totalScheduled += sched;

        List<BillingCycle> pending = cycles.findPendingUpTo(processUntil);
        for (BillingCycle c : pending) {
          try {
            // triggeredBy=null indica origem batch — listener financeiro/auditoria tratam ausência.
            generateService.generateForCycle(c.typedId(), null);
            totalGenerated++;
          } catch (RuntimeException e) {
            log.warn("Cycle generation failed tenant={} cycleId={}", tenant.slug(), c.typedId(), e);
            totalFailed++;
          }
        }
      } catch (RuntimeException e) {
        log.error("GenerateBillingCyclesJob: tenant {} failed", tenant.slug(), e);
      } finally {
        TenantContext.clear();
      }
    }

    log.info(
        "GenerateBillingCyclesJob done scheduled={} generated={} failed={}",
        totalScheduled,
        totalGenerated,
        totalFailed);
  }
}
