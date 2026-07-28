package dev.dimi.erp.produto.internal.web;

import dev.dimi.erp.identidade.AuthenticatedUser;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.produto.internal.application.ManageProductService;
import dev.dimi.erp.produto.internal.web.dto.PackagingApiResponse;
import dev.dimi.erp.produto.internal.web.dto.ProductApiResponse;
import dev.dimi.erp.produto.internal.web.dto.ProductPageApiResponse;
import dev.dimi.erp.produto.internal.web.dto.RegisterPackagingApiRequest;
import dev.dimi.erp.produto.internal.web.dto.RegisterProductApiRequest;
import dev.dimi.erp.produto.internal.web.dto.RegisterVariantApiRequest;
import dev.dimi.erp.produto.internal.web.dto.UpdatePackagingApiRequest;
import dev.dimi.erp.produto.internal.web.dto.UpdateProductApiRequest;
import dev.dimi.erp.produto.internal.web.dto.VariantApiResponse;
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
 * Endpoints REST de {@code product}, {@code variant} e {@code packaging} ({@code
 * /api/v1/produtos/**}). Permissions:
 *
 * <ul>
 *   <li>{@code produto.consultar} — listar, buscar, search por nome.
 *   <li>{@code produto.criar} — registrar novo produto/variant/packaging.
 *   <li>{@code produto.editar} — alterar dados, desativar/reativar, marcar default.
 * </ul>
 *
 * <p>Permissão de preço ({@code produto.preco.*}) fica num controller separado quando o slice
 * incluir tabelas de preço.
 */
@RestController
@RequestMapping("/api/v1/produtos")
@RequiredArgsConstructor
class ProductController {

  private static final int MAX_PAGE_SIZE = 100;

  private final ManageProductService products;

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('produto.criar')")
  ProductApiResponse register(
      @RequestBody @Valid RegisterProductApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ProductApiResponse.from(products.register(body.toCommand(principal.userId())));
  }

  @GetMapping
  @PreAuthorize("hasAuthority('produto.consultar')")
  ProductPageApiResponse list(
      @RequestParam(name = "q", required = false) String query,
      @RequestParam(name = "page", defaultValue = "0") int page,
      @RequestParam(name = "size", defaultValue = "20") int size) {
    Pageable pageable = PageRequest.of(Math.max(0, page), clampPageSize(size));
    if (query != null && !query.isBlank()) {
      return ProductPageApiResponse.from(products.searchByName(query.trim(), pageable));
    }
    return ProductPageApiResponse.from(products.list(pageable));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('produto.consultar')")
  ProductApiResponse findById(@PathVariable("id") UUID id) {
    return ProductApiResponse.from(products.findById(new ProductId(id)));
  }

  @PatchMapping("/{id}")
  @PreAuthorize("hasAuthority('produto.editar')")
  ProductApiResponse update(
      @PathVariable("id") UUID id,
      @RequestBody @Valid UpdateProductApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return ProductApiResponse.from(
        products.update(body.toCommand(new ProductId(id), principal.userId())));
  }

  @PatchMapping("/{id}/desativar")
  @PreAuthorize("hasAuthority('produto.editar')")
  ProductApiResponse deactivate(@PathVariable("id") UUID id) {
    return ProductApiResponse.from(products.deactivate(new ProductId(id)));
  }

  @PatchMapping("/{id}/reativar")
  @PreAuthorize("hasAuthority('produto.editar')")
  ProductApiResponse reactivate(@PathVariable("id") UUID id) {
    return ProductApiResponse.from(products.reactivate(new ProductId(id)));
  }

  // ---------------------------------------------------------------------------
  // Variants
  // ---------------------------------------------------------------------------

  @PostMapping("/{id}/variants")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('produto.editar')")
  VariantApiResponse addVariant(
      @PathVariable("id") UUID id,
      @RequestBody @Valid RegisterVariantApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return VariantApiResponse.from(
        products.addVariant(body.toCommand(new ProductId(id), principal.userId())));
  }

  // ---------------------------------------------------------------------------
  // Packagings
  // ---------------------------------------------------------------------------

  @PostMapping("/{id}/variants/{variantId}/packagings")
  @ResponseStatus(HttpStatus.CREATED)
  @PreAuthorize("hasAuthority('produto.editar')")
  PackagingApiResponse addPackaging(
      @PathVariable("id") UUID productId,
      @PathVariable("variantId") UUID variantId,
      @RequestBody @Valid RegisterPackagingApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PackagingApiResponse.from(
        products.addPackaging(body.toCommand(variantId, principal.userId())));
  }

  @GetMapping("/packagings/{packagingId}")
  @PreAuthorize("hasAuthority('produto.consultar')")
  PackagingApiResponse findPackagingById(@PathVariable("packagingId") UUID packagingId) {
    return PackagingApiResponse.from(products.findPackagingById(new PackagingId(packagingId)));
  }

  @PatchMapping("/packagings/{packagingId}")
  @PreAuthorize("hasAuthority('produto.editar')")
  PackagingApiResponse updatePackaging(
      @PathVariable("packagingId") UUID packagingId,
      @RequestBody @Valid UpdatePackagingApiRequest body,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PackagingApiResponse.from(
        products.updatePackaging(body.toCommand(new PackagingId(packagingId), principal.userId())));
  }

  @PatchMapping("/packagings/{packagingId}/padrao")
  @PreAuthorize("hasAuthority('produto.editar')")
  PackagingApiResponse markPackagingDefault(@PathVariable("packagingId") UUID packagingId) {
    return PackagingApiResponse.from(products.markPackagingDefault(new PackagingId(packagingId)));
  }

  @PatchMapping("/packagings/{packagingId}/desativar")
  @PreAuthorize("hasAuthority('produto.editar')")
  PackagingApiResponse deactivatePackaging(
      @PathVariable("packagingId") UUID packagingId,
      @AuthenticationPrincipal AuthenticatedUser principal) {
    return PackagingApiResponse.from(
        products.deactivatePackaging(new PackagingId(packagingId), principal.userId()));
  }

  private static int clampPageSize(int requested) {
    if (requested <= 0) {
      return 20;
    }
    return Math.min(requested, MAX_PAGE_SIZE);
  }
}
