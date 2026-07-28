package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.CertificateExpiringEvent;
import dev.dimi.erp.cadastros.internal.domain.BranchFiscal;
import dev.dimi.erp.cadastros.internal.persistence.BranchFiscalRepository;
import dev.dimi.erp.identidade.TenantQueryPort;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Job diário que detecta certificados A1 próximos da expiração (RN-CAD-009) e publica um {@link
 * CertificateExpiringEvent} para cada marco — 30, 15 e 7 dias. Iteração cross-tenant via {@link
 * TenantQueryPort} + {@link TenantContext}.
 *
 * <p>Disparo: cron {@code 0 0 6 * * *} (06:00 UTC) — pré-horário comercial no Brasil. Habilitado
 * por padrão; desligar com {@code cadastros.certificate.expiry-job.enabled=false} em ambientes de
 * teste ou cluster passivo.
 */
@Component
@ConditionalOnProperty(
    name = "erp-poc.cadastros.certificate.expiry-job.enabled",
    havingValue = "true",
    matchIfMissing = true)
@RequiredArgsConstructor
@Slf4j
public class CertificateExpiryJob {

  private static final List<Integer> ALERT_DAYS = List.of(30, 15, 7);

  private final TenantQueryPort tenants;
  private final BranchFiscalRepository branchFiscals;
  private final ApplicationEventPublisher events;

  @Scheduled(cron = "${erp-poc.cadastros.certificate.expiry-job.cron:0 0 6 * * *}")
  public void run() {
    Instant now = Instant.now();
    List<Tenant> active = tenants.findAllActive();
    log.info("certificate expiry job: scanning {} tenants", active.size());
    int published = 0;
    for (Tenant tenant : active) {
      try {
        TenantContext.set(tenant);
        published += scanTenant(tenant, now);
      } catch (RuntimeException e) {
        log.error("certificate expiry job: tenant {} failed", tenant.slug(), e);
      } finally {
        TenantContext.clear();
      }
    }
    log.info("certificate expiry job: published {} events", published);
  }

  int scanTenant(Tenant tenant, Instant now) {
    Instant windowEnd = now.plus(31, ChronoUnit.DAYS);
    List<BranchFiscal> candidates = branchFiscals.findCertificatesExpiringBetween(now, windowEnd);
    int published = 0;
    TenantId tenantId = new TenantId(tenant.id());
    for (BranchFiscal f : candidates) {
      long daysRemaining = Duration.between(now, f.getCertValidUntil()).toDays();
      // Duration#toDays trunca; janela "exatamente X dias" = [X*24h, (X+1)*24h)
      int marker = matchMarker(daysRemaining);
      if (marker < 0) {
        continue;
      }
      events.publishEvent(
          new CertificateExpiringEvent(
              new BranchId(f.getBranchId()),
              tenantId,
              f.getCertThumbprint(),
              f.getCertValidUntil(),
              marker,
              now));
      published++;
      log.info(
          "certificate expiring tenant={} branch={} thumbprint={} daysRemaining={}",
          tenant.slug(),
          f.getBranchId(),
          f.getCertThumbprint(),
          marker);
    }
    return published;
  }

  private static int matchMarker(long daysRemaining) {
    for (int marker : ALERT_DAYS) {
      if (daysRemaining == marker) {
        return marker;
      }
    }
    return -1;
  }
}
