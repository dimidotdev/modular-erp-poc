package dev.dimi.erp.contratos.internal.web;

import dev.dimi.erp.contratos.BillingCycleId;
import dev.dimi.erp.contratos.ContractId;
import dev.dimi.erp.contratos.internal.application.ContractQueryService;
import dev.dimi.erp.contratos.internal.application.GenerateBillingCyclesJob;
import dev.dimi.erp.contratos.internal.application.RegenerateBillingCycleService;
import dev.dimi.erp.contratos.internal.web.dto.BillingCycleApiResponse;
import dev.dimi.erp.identidade.AuthenticatedUser;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code contract_billing_cycle} (sob {@code /api/v1/contratos/...}).
 *
 * <p>Slice 10.2: listagem por contrato + regeneração manual de ciclo {@code FAILED} + disparo
 * manual do job de geração. Controller separado de {@link ContractController} para manter
 * responsabilidades distintas — mesmo padrão de {@code OsBillingController} vs {@code
 * ServiceOrderController} no slice 9.2b.
 *
 * <p>O endpoint {@code POST /billing-cycles/run-now} dispara o mesmo fluxo do job recorrente mas
 * sem {@code @SchedulerLock} — a permission {@code contrato.faturar} é authoritative sobre
 * concorrência. Útil em dev/teste e para janelas de catch-up; em produção o cron diário cobre o
 * caso comum.
 */
@RestController
@RequestMapping("/api/v1/contratos")
@RequiredArgsConstructor
class BillingCycleController {

  private static final ZoneId BRT = ZoneId.of("America/Sao_Paulo");

  private final ContractQueryService queryService;
  private final RegenerateBillingCycleService regenerateService;
  private final GenerateBillingCyclesJob job;

  @GetMapping("/{id}/billing-cycles")
  @PreAuthorize("hasAuthority('contrato.consultar')")
  public List<BillingCycleApiResponse> listCycles(@PathVariable("id") UUID id) {
    return queryService.findCycles(new ContractId(id)).stream()
        .map(BillingCycleApiResponse::from)
        .toList();
  }

  @PostMapping("/{id}/billing-cycles/{cycleId}/regenerate")
  @PreAuthorize("hasAuthority('contrato.faturar')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void regenerate(
      @PathVariable("id") UUID id,
      @PathVariable("cycleId") UUID cycleId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    regenerateService.regenerate(new BillingCycleId(cycleId), principal.userId());
  }

  /**
   * Dispara manualmente o job de geração de ciclos para a data informada (ou hoje em BRT). Em
   * produção o cron diário ({@code 0 0 3 * * *}) cuida do fluxo normal; este endpoint serve a
   * dev/teste e a catch-up após indisponibilidade. Roda na request thread sem
   * {@code @SchedulerLock} — caller já é authoritative via permission {@code contrato.faturar}.
   */
  @PostMapping("/billing-cycles/run-now")
  @PreAuthorize("hasAuthority('contrato.faturar')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void runNow(
      @RequestParam(value = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
          LocalDate date) {
    LocalDate today = date != null ? date : LocalDate.now(BRT);
    job.runManually(today);
  }
}
