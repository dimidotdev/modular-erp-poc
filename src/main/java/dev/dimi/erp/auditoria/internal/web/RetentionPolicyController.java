package dev.dimi.erp.auditoria.internal.web;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.internal.application.AuditRetentionService;
import dev.dimi.erp.auditoria.internal.application.RetentionPolicyService;
import dev.dimi.erp.auditoria.internal.web.dto.RetentionPolicyApiResponse;
import dev.dimi.erp.auditoria.internal.web.dto.RetentionRunApiResponse;
import dev.dimi.erp.auditoria.internal.web.dto.UpdateRetentionPolicyRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de administração de políticas de retenção e da trilha histórica de execuções
 * ({@code /api/v1/auditoria/retention/**}).
 *
 * <p>Todas as rotas exigem {@code audit.administrar} — não basta {@code audit.consultar}.
 *
 * <p>{@link #runNow(AuthenticatedUser)} executa síncronamente na request thread (aceitando a
 * latência em V1); um job assíncrono separado já roda em background via
 * {@code @Scheduled}+{@code @SchedulerLock}.
 */
@RestController
@RequestMapping("/api/v1/auditoria/retention")
@RequiredArgsConstructor
class RetentionPolicyController {

  private final RetentionPolicyService policyService;
  private final AuditRetentionService retentionService;
  private final StepUpPolicy stepUpPolicy;

  @GetMapping("/policies")
  @PreAuthorize("hasAuthority('audit.administrar')")
  public List<RetentionPolicyApiResponse> listPolicies() {
    return policyService.listPolicies().stream().map(RetentionPolicyApiResponse::from).toList();
  }

  @PatchMapping("/policies/{category}")
  @PreAuthorize("hasAuthority('audit.administrar')")
  public RetentionPolicyApiResponse update(
      @PathVariable AuditCategory category,
      @Valid @RequestBody UpdateRetentionPolicyRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    var updated =
        policyService.updatePolicy(
            category, body.retainDays(), body.actionAtEol(), principal.userId());
    return RetentionPolicyApiResponse.from(updated);
  }

  @GetMapping("/runs")
  @PreAuthorize("hasAuthority('audit.administrar')")
  public List<RetentionRunApiResponse> listRuns() {
    return policyService.listRecentRuns(10).stream().map(RetentionRunApiResponse::from).toList();
  }

  @PostMapping("/run-now")
  @PreAuthorize("hasAuthority('audit.administrar')")
  @ResponseStatus(HttpStatus.ACCEPTED)
  public void runNow(@AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    retentionService.runRetention();
  }
}
