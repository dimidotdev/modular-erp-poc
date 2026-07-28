package dev.dimi.erp.produto.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.internal.application.ManageBrandService;
import dev.dimi.erp.produto.internal.web.dto.BrandApiResponse;
import dev.dimi.erp.produto.internal.web.dto.CreateBrandApiRequest;
import dev.dimi.erp.produto.internal.web.dto.RenameBrandApiRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
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
 * Endpoints REST de {@code catalog_brand} ({@code /api/v1/produtos/brands/**}). Reaproveita as
 * permissions de produto — marca faz parte do catálogo do mesmo módulo, não justifica permissions
 * próprias no v1.
 */
@RestController
@RequestMapping("/api/v1/produtos/brands")
@RequiredArgsConstructor
class BrandController {

  private final ManageBrandService brands;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('produto.criar')")
  BrandApiResponse register(
      @RequestBody @Valid CreateBrandApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return BrandApiResponse.from(brands.register(body.toCommand(principal.userId())));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('produto.consultar')")
  List<BrandApiResponse> list() {
    return brands.list().stream().map(BrandApiResponse::from).toList();
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('produto.consultar')")
  BrandApiResponse findById(@PathVariable("id") UUID id) {
    return BrandApiResponse.from(brands.findById(new BrandId(id)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('produto.editar')")
  BrandApiResponse rename(
      @PathVariable("id") UUID id, @RequestBody @Valid RenameBrandApiRequest body) {
    return BrandApiResponse.from(brands.rename(new BrandId(id), body.name()));
  }

  @PatchMapping("/{id}/desativar")
  @PreAuthorize("hasAuthority('produto.editar')")
  BrandApiResponse deactivate(@PathVariable("id") UUID id) {
    return BrandApiResponse.from(brands.deactivate(new BrandId(id)));
  }

  @PatchMapping("/{id}/reativar")
  @PreAuthorize("hasAuthority('produto.editar')")
  BrandApiResponse reactivate(@PathVariable("id") UUID id) {
    return BrandApiResponse.from(brands.reactivate(new BrandId(id)));
  }
}
