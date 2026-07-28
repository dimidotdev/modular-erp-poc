package dev.dimi.erp.cadastros.internal.web;

import dev.dimi.erp.cadastros.PersonContactId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.PersonContactService;
import dev.dimi.erp.cadastros.internal.web.dto.CreatePersonContactApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.PersonContactApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.UpdatePersonContactApiRequest;
import dev.dimi.erp.identidade.AuthenticatedUser;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de {@code PersonContact} ({@code /api/v1/cadastros/persons/{id}/contatos/**}).
 *
 * <p>Permissões: {@code pessoa.consultar} para leitura, {@code pessoa.editar} para mutações.
 */
@RestController
@RequestMapping("/api/v1/cadastros/persons/{personId}/contatos")
@RequiredArgsConstructor
class PersonContactController {

  private final PersonContactService service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonContactApiResponse create(
      @PathVariable("personId") UUID personId,
      @RequestBody @Valid CreatePersonContactApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonContactApiResponse.from(
        service.create(body.toCommand(new PersonId(personId), principal.userId())));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('pessoa.consultar')")
  List<PersonContactApiResponse> list(@PathVariable("personId") UUID personId) {
    return service.list(new PersonId(personId)).stream()
        .map(PersonContactApiResponse::from)
        .toList();
  }

  @GetMapping("/{contactId}")
  @PreAuthorize("hasAuthority('pessoa.consultar')")
  PersonContactApiResponse findById(
      @PathVariable("personId") UUID personId, @PathVariable("contactId") UUID contactId) {
    return PersonContactApiResponse.from(
        service.findById(new PersonId(personId), new PersonContactId(contactId)));
  }

  @PatchMapping("/{contactId}")
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonContactApiResponse update(
      @PathVariable("personId") UUID personId,
      @PathVariable("contactId") UUID contactId,
      @RequestBody @Valid UpdatePersonContactApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonContactApiResponse.from(
        service.update(
            body.toCommand(
                new PersonId(personId), new PersonContactId(contactId), principal.userId())));
  }

  @PostMapping("/{contactId}/primario")
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonContactApiResponse setAsPrimary(
      @PathVariable("personId") UUID personId,
      @PathVariable("contactId") UUID contactId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonContactApiResponse.from(
        service.setAsPrimary(
            new PersonId(personId), new PersonContactId(contactId), principal.userId()));
  }

  @DeleteMapping("/{contactId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('pessoa.editar')")
  void delete(
      @PathVariable("personId") UUID personId,
      @PathVariable("contactId") UUID contactId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    service.delete(new PersonId(personId), new PersonContactId(contactId), principal.userId());
  }
}
