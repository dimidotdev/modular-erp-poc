package dev.dimi.erp.movimentacao.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import dev.dimi.erp.movimentacao.OutboundVoucherId;
import dev.dimi.erp.movimentacao.internal.application.IssueOutboundVoucherService;
import dev.dimi.erp.movimentacao.internal.web.dto.IssueOutboundVoucherApiRequest;
import dev.dimi.erp.movimentacao.internal.web.dto.OutboundVoucherApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code outbound_voucher} ({@code /api/v1/movimentacao/outbound-vouchers/**}).
 * RN-MOV-023: emissão exige step-up + justificativa.
 */
@RestController
@RequestMapping("/api/v1/movimentacao/outbound-vouchers")
@RequiredArgsConstructor
class OutboundVoucherController {

  private final IssueOutboundVoucherService vouchers;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('saida-avulsa.criar')")
  OutboundVoucherApiResponse issue(
      @RequestBody @Valid IssueOutboundVoucherApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return OutboundVoucherApiResponse.from(vouchers.issue(body.toCommand(principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('saida-avulsa.consultar')")
  OutboundVoucherApiResponse findById(@PathVariable("id") UUID id) {
    return OutboundVoucherApiResponse.from(vouchers.findById(new OutboundVoucherId(id)));
  }
}
