package dev.dimi.erp.cadastros.internal.web;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.internal.application.BranchCertificateService;
import dev.dimi.erp.cadastros.internal.application.BranchService;
import dev.dimi.erp.cadastros.internal.application.InstallBranchCertificateCommand;
import dev.dimi.erp.cadastros.internal.web.dto.BranchApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.BranchPageApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.DeactivateBranchApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.RegisterBranchApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.UpdateBranchApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.UpsertBranchFiscalApiRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import jakarta.validation.Valid;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints REST de {@code Branch} ({@code /api/v1/cadastros/branches/**}).
 *
 * <p>Permissions:
 *
 * <ul>
 *   <li>{@code filial.consultar} — listar, buscar por id.
 *   <li>{@code filial.criar} + step-up — registrar nova filial.
 *   <li>{@code filial.editar} — atualizar, desativar/reativar.
 *   <li>{@code filial.fiscal.editar} + step-up — upsert de {@code branch_fiscal}.
 *   <li>{@code filial.certificado.gerenciar} + step-up — instalar/remover certificado A1.
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/cadastros/branches")
@RequiredArgsConstructor
class BranchController {

  private static final int MAX_PAGE_SIZE = 100;

  private final BranchService branchService;
  private final BranchCertificateService certificateService;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('filial.criar')")
  BranchApiResponse register(
      @RequestBody @Valid RegisterBranchApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return BranchApiResponse.from(branchService.register(body.toCommand(principal.userId())));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('filial.consultar')")
  BranchPageApiResponse list(
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    return BranchPageApiResponse.from(branchService.list(pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('filial.consultar')")
  BranchApiResponse findById(@PathVariable("id") UUID id) {
    return BranchApiResponse.from(branchService.findById(new BranchId(id)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('filial.editar')")
  BranchApiResponse update(
      @PathVariable("id") UUID id,
      @RequestBody @Valid UpdateBranchApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return BranchApiResponse.from(
        branchService.update(body.toCommand(new BranchId(id), principal.userId())));
  }

  @PatchMapping("/{id}/desativar")
  @PreAuthorize("hasAuthority('filial.editar')")
  BranchApiResponse deactivate(
      @PathVariable("id") UUID id,
      @RequestBody @Valid DeactivateBranchApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return BranchApiResponse.from(
        branchService.deactivate(new BranchId(id), body.reason(), principal.userId()));
  }

  @PatchMapping("/{id}/reativar")
  @PreAuthorize("hasAuthority('filial.editar')")
  BranchApiResponse reactivate(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return BranchApiResponse.from(branchService.reactivate(new BranchId(id), principal.userId()));
  }

  /**
   * Upsert de dados fiscais (CNPJ, regime, inscrições). CNPJ exigido apenas no primeiro registro e
   * imutável depois. Certificado A1 entra por endpoint próprio no slice 3.5.
   */
  @PutMapping("/{id}/fiscal")
  @PreAuthorize("hasAuthority('filial.fiscal.editar')")
  BranchApiResponse upsertFiscal(
      @PathVariable("id") UUID id,
      @RequestBody @Valid UpsertBranchFiscalApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return BranchApiResponse.from(
        branchService.upsertFiscal(body.toCommand(new BranchId(id), principal.userId())));
  }

  /**
   * Instala (ou substitui) o certificado A1 da filial. Multipart com dois parts: {@code pfx}
   * (arquivo .pfx/.p12) e {@code password} (senha do PKCS#12, form field). RN-CAD-007: o material
   * vai para o Azure Key Vault; metadados (thumbprint, validade, CNPJ) ficam em {@code
   * branch_fiscal}. Step-up obrigatório por ser ação sensível.
   */
  @PutMapping(value = "/{id}/certificate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  @PreAuthorize("hasAuthority('filial.certificado.gerenciar')")
  BranchApiResponse installCertificate(
      @PathVariable("id") UUID id,
      @RequestPart("pfx") MultipartFile pfx,
      @RequestParam("password") String password,
      @AuthenticationPrincipal AuthenticatedUser principal)
      throws IOException {
    stepUpPolicy.requireFresh(principal);
    if (pfx == null || pfx.isEmpty()) {
      throw new IllegalArgumentException("arquivo PFX obrigatório");
    }
    if (password == null || password.isEmpty()) {
      throw new IllegalArgumentException("senha obrigatória");
    }
    InstallBranchCertificateCommand cmd =
        new InstallBranchCertificateCommand(
            new BranchId(id), pfx.getBytes(), password, principal.userId());
    return BranchApiResponse.from(certificateService.install(cmd));
  }

  /**
   * Remove o certificado A1 da filial (soft-delete no Key Vault: retenção 90 dias). A filial fica
   * sem certificado e não pode emitir NFe até reinstalar.
   */
  @DeleteMapping("/{id}/certificate")
  @PreAuthorize("hasAuthority('filial.certificado.gerenciar')")
  BranchApiResponse clearCertificate(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return BranchApiResponse.from(certificateService.clear(new BranchId(id), principal.userId()));
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
