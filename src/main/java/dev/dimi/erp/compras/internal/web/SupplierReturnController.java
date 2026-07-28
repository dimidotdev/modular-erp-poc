package dev.dimi.erp.compras.internal.web;

import dev.dimi.erp.compras.SupplierReturnId;
import dev.dimi.erp.compras.internal.application.IssueSupplierReturnService;
import dev.dimi.erp.compras.internal.web.dto.IssueSupplierReturnApiRequest;
import dev.dimi.erp.compras.internal.web.dto.SupplierReturnApiResponse;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
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
 * Endpoints REST de devolução a fornecedor ({@code /api/v1/compras/supplier-returns/**}). Emissão
 * exige step-up + permission {@code compra.devolver-fornecedor} (RN-COM-021).
 */
@RestController
@RequestMapping("/api/v1/compras/supplier-returns")
@RequiredArgsConstructor
class SupplierReturnController {

  private final IssueSupplierReturnService service;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('compra.devolver-fornecedor')")
  SupplierReturnApiResponse issue(
      @RequestBody @Valid IssueSupplierReturnApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return SupplierReturnApiResponse.from(service.issue(body.toCommand(principal.userId())));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('compra.consultar')")
  SupplierReturnApiResponse findById(@PathVariable("id") UUID id) {
    return SupplierReturnApiResponse.from(service.findById(new SupplierReturnId(id)));
  }
}
