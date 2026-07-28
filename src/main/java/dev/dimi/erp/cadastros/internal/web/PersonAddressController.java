package dev.dimi.erp.cadastros.internal.web;

import dev.dimi.erp.cadastros.PersonAddressId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.application.PersonAddressService;
import dev.dimi.erp.cadastros.internal.web.dto.CreatePersonAddressApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.PersonAddressApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.UpdatePersonAddressApiRequest;
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
 * Endpoints REST de {@code PersonAddress} ({@code /api/v1/cadastros/persons/{id}/enderecos/**}).
 *
 * <p>Permissões: {@code pessoa.consultar} para leitura, {@code pessoa.editar} para mutações
 * (criar/atualizar/marcar padrão/remover). Endereços são hard-delete — auditoria fica via evento.
 */
@RestController
@RequestMapping("/api/v1/cadastros/persons/{personId}/enderecos")
@RequiredArgsConstructor
class PersonAddressController {

  private final PersonAddressService service;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonAddressApiResponse create(
      @PathVariable("personId") UUID personId,
      @RequestBody @Valid CreatePersonAddressApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonAddressApiResponse.from(
        service.create(body.toCommand(new PersonId(personId), principal.userId())));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('pessoa.consultar')")
  List<PersonAddressApiResponse> list(@PathVariable("personId") UUID personId) {
    return service.list(new PersonId(personId)).stream()
        .map(PersonAddressApiResponse::from)
        .toList();
  }

  @GetMapping("/{addressId}")
  @PreAuthorize("hasAuthority('pessoa.consultar')")
  PersonAddressApiResponse findById(
      @PathVariable("personId") UUID personId, @PathVariable("addressId") UUID addressId) {
    return PersonAddressApiResponse.from(
        service.findById(new PersonId(personId), new PersonAddressId(addressId)));
  }

  @PatchMapping("/{addressId}")
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonAddressApiResponse update(
      @PathVariable("personId") UUID personId,
      @PathVariable("addressId") UUID addressId,
      @RequestBody @Valid UpdatePersonAddressApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonAddressApiResponse.from(
        service.update(
            body.toCommand(
                new PersonId(personId), new PersonAddressId(addressId), principal.userId())));
  }

  @PostMapping("/{addressId}/padrao")
  @PreAuthorize("hasAuthority('pessoa.editar')")
  PersonAddressApiResponse setAsDefault(
      @PathVariable("personId") UUID personId,
      @PathVariable("addressId") UUID addressId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PersonAddressApiResponse.from(
        service.setAsDefault(
            new PersonId(personId), new PersonAddressId(addressId), principal.userId()));
  }

  @DeleteMapping("/{addressId}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  @PreAuthorize("hasAuthority('pessoa.editar')")
  void delete(
      @PathVariable("personId") UUID personId,
      @PathVariable("addressId") UUID addressId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    service.delete(new PersonId(personId), new PersonAddressId(addressId), principal.userId());
  }
}
