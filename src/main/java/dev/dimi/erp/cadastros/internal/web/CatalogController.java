package dev.dimi.erp.cadastros.internal.web;

import dev.dimi.erp.cadastros.internal.application.CatalogService;
import dev.dimi.erp.cadastros.internal.application.CatalogType;
import dev.dimi.erp.cadastros.internal.application.UnknownCatalogTypeException;
import dev.dimi.erp.cadastros.internal.web.dto.CatalogEntryApiResponse;
import dev.dimi.erp.cadastros.internal.web.dto.CatalogEntryCreateApiRequest;
import dev.dimi.erp.cadastros.internal.web.dto.CatalogEntryRenameApiRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST de catálogos auxiliares ({@code /api/v1/cadastros/catalogs/{type}/**}). O segmento
 * {@code {type}} aceita slugs kebab-case definidos em {@link CatalogType} (ex: {@code segments},
 * {@code address-types}, {@code measurement-units}); slug desconhecido retorna 404.
 *
 * <p>Permissions:
 *
 * <ul>
 *   <li>{@code catalogo.consultar} — listar e buscar por id.
 *   <li>{@code catalogo.editar} — criar, renomear, desativar/reativar entradas custom.
 * </ul>
 *
 * <p>Entradas {@code system=true} (seed) são imutáveis (RN-CAD-013).
 */
@RestController
@RequestMapping("/api/v1/cadastros/catalogs")
@RequiredArgsConstructor
class CatalogController {

  private final CatalogService catalogService;

  @GetMapping("/{type}")
  @PreAuthorize("hasAuthority('catalogo.consultar')")
  List<CatalogEntryApiResponse> list(@PathVariable("type") String type) {
    return catalogService.list(parseType(type)).stream()
        .map(CatalogEntryApiResponse::from)
        .toList();
  }

  @GetMapping("/{type}/{id}")
  @PreAuthorize("hasAuthority('catalogo.consultar')")
  CatalogEntryApiResponse findById(@PathVariable("type") String type, @PathVariable("id") UUID id) {
    return CatalogEntryApiResponse.from(catalogService.findById(parseType(type), id));
  }

  @PostMapping("/{type}")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('catalogo.editar')")
  CatalogEntryApiResponse create(
      @PathVariable("type") String type, @RequestBody @Valid CatalogEntryCreateApiRequest body) {
    return CatalogEntryApiResponse.from(
        catalogService.create(parseType(type), body.code(), body.name()));
  }

  @PatchMapping("/{type}/{id}")
  @PreAuthorize("hasAuthority('catalogo.editar')")
  CatalogEntryApiResponse rename(
      @PathVariable("type") String type,
      @PathVariable("id") UUID id,
      @RequestBody @Valid CatalogEntryRenameApiRequest body) {
    return CatalogEntryApiResponse.from(catalogService.rename(parseType(type), id, body.name()));
  }

  @PatchMapping("/{type}/{id}/desativar")
  @PreAuthorize("hasAuthority('catalogo.editar')")
  CatalogEntryApiResponse deactivate(
      @PathVariable("type") String type, @PathVariable("id") UUID id) {
    return CatalogEntryApiResponse.from(catalogService.deactivate(parseType(type), id));
  }

  @PatchMapping("/{type}/{id}/reativar")
  @PreAuthorize("hasAuthority('catalogo.editar')")
  CatalogEntryApiResponse reactivate(
      @PathVariable("type") String type, @PathVariable("id") UUID id) {
    return CatalogEntryApiResponse.from(catalogService.reactivate(parseType(type), id));
  }

  private static CatalogType parseType(String slug) {
    return CatalogType.fromSlug(slug).orElseThrow(() -> new UnknownCatalogTypeException(slug));
  }
}
