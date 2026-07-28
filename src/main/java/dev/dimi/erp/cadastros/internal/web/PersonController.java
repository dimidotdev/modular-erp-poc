package dev.dimi.erp.cadastros.internal.web;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.PersonService;
import dev.dimi.erp.cadastros.internal.application.PersonalDataExportService;
import dev.dimi.erp.cadastros.internal.web.dto.AnonymizePersonApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.DeactivateModalityApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.DeactivatePersonApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.PersonApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.PersonPageApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.PersonalDataExportApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.RegisterPersonApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.UpdatePersonApiRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.identidade.StepUpPolicy;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code Person} ({@code /api/v1/cadastros/persons/**}).
 *
 * <p>Todas as operações exigem autenticação JWT e permission granular do domínio cadastros:
 *
 * <ul>
 *   <li>{@code pessoa.consultar} — listar, buscar por id, busca por nome.
 *   <li>{@code pessoa.criar} — registrar nova pessoa.
 *   <li>{@code pessoa.editar} — atualizar, ativar/desativar modalidade, reativar.
 *   <li>{@code pessoa.deletar} — soft-delete.
 *   <li>{@code pessoa.anonimizar} — anonimização LGPD (+ step-up).
 *   <li>{@code lgpd.consultar} — export consolidado de dados pessoais (+ step-up).
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/cadastros/persons")
@RequiredArgsConstructor
class PersonController {

  private static final int MAX_PAGE_SIZE = 100;

  private final PersonService personService;
  private final PersonalDataExportService personalDataExportService;
  private final StepUpPolicy stepUpPolicy;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('pessoa.criar')")
  PersonApiResponse register(
      @RequestBody @Valid RegisterPersonApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonApiResponse.from(personService.register(body.toCommand(principal.userId())));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('pessoa.consultar')")
  PersonPageApiResponse list(
      @RequestParam(name = "q", required = false) String query,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    if (query != null && !query.isBlank()) {
      return PersonPageApiResponse.from(personService.searchByLegalName(query.trim(), pageable));
    }
    return PersonPageApiResponse.from(personService.list(pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('pessoa.consultar')")
  PersonApiResponse findById(@PathVariable("id") UUID id) {
    return PersonApiResponse.from(personService.findById(new PersonId(id)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonApiResponse update(
      @PathVariable("id") UUID id,
      @RequestBody @Valid UpdatePersonApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonApiResponse.from(
        personService.update(body.toCommand(new PersonId(id), principal.userId())));
  }

  @PatchMapping("/{id}/desativar")
  @PreAuthorize("hasAuthority('pessoa.deletar')")
  PersonApiResponse deactivate(
      @PathVariable("id") UUID id,
      @RequestBody @Valid DeactivatePersonApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonApiResponse.from(
        personService.deactivate(new PersonId(id), body.reason(), principal.userId()));
  }

  @PatchMapping("/{id}/reativar")
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonApiResponse reactivate(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonApiResponse.from(personService.reactivate(new PersonId(id), principal.userId()));
  }

  @PostMapping("/{id}/modalidades/{modality}/ativar")
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonApiResponse activateModality(
      @PathVariable("id") UUID id,
      @PathVariable("modality") Modality modality,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonApiResponse.from(
        personService.activateModality(new PersonId(id), modality, principal.userId()));
  }

  @PostMapping("/{id}/modalidades/{modality}/desativar")
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonApiResponse deactivateModality(
      @PathVariable("id") UUID id,
      @PathVariable("modality") Modality modality,
      @RequestBody @Valid DeactivateModalityApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonApiResponse.from(
        personService.deactivateModality(
            new PersonId(id), modality, body.reason(), principal.userId()));
  }

  /**
   * Anonimização LGPD (RN-CAD-011/012). Exige {@code pessoa.anonimizar} + step-up dentro da janela
   * configurada em {@code erp-poc.security.jwt.step-up-ttl}. Operação irreversível: PII é zerada,
   * endereços/contatos apagados, modalidades desativadas.
   */
  @PostMapping("/{id}/anonimizar")
  @PreAuthorize("hasAuthority('pessoa.anonimizar')")
  PersonApiResponse anonymize(
      @PathVariable("id") UUID id,
      @RequestBody @Valid AnonymizePersonApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return PersonApiResponse.from(
        personService.anonymize(new PersonId(id), body.reason(), principal.userId()));
  }

  /**
   * Export consolidado de dados pessoais (RN-CAD-016 / LGPD Art. 18 V). Exige {@code
   * lgpd.consultar} + step-up: PII bruta sai no payload, então tratamos como operação sensível
   * mesmo sendo somente-leitura. v1 cobre só o que o módulo cadastros conhece; outros módulos
   * plugam Query Ports quando existirem.
   */
  @GetMapping("/{id}/personal-data-export")
  @PreAuthorize("hasAuthority('lgpd.consultar')")
  PersonalDataExportApiResponse exportPersonalData(
      @PathVariable("id") UUID id, @AuthenticationPrincipal AuthenticatedUser principal) {
    stepUpPolicy.requireFresh(principal);
    return PersonalDataExportApiResponse.from(
        personalDataExportService.export(new PersonId(id), principal.userId()));
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
