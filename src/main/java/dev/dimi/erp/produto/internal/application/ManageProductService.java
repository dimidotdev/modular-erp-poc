package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.Classification;
import dev.dimi.erp.produto.PackagingCreatedEvent;
import dev.dimi.erp.produto.PackagingDeactivatedEvent;
import dev.dimi.erp.produto.PackagingId;
import dev.dimi.erp.produto.ProductCreatedEvent;
import dev.dimi.erp.produto.ProductId;
import dev.dimi.erp.produto.internal.domain.Packaging;
import dev.dimi.erp.produto.internal.domain.Product;
import dev.dimi.erp.produto.internal.domain.Variant;
import dev.dimi.erp.produto.internal.persistence.CatalogBrandRepository;
import dev.dimi.erp.produto.internal.persistence.PackagingRepository;
import dev.dimi.erp.produto.internal.persistence.ProductRepository;
import dev.dimi.erp.produto.internal.persistence.VariantRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações de produto, variant e packaging dentro do tenant atual. Emite eventos de domínio para o
 * módulo {@code auditoria} (e futuramente {@code estoque}) ao final das transações.
 *
 * <p>RN-PRD-001/003: register cria o produto + variant default + (opcional) packaging default em
 * uma única transação para garantir que toda hierarquia exista mesmo quando o tenant trabalha "sem
 * variedades".
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManageProductService {

  private static final String DEFAULT_VARIANT_CODE = "PADRAO";
  private static final String DEFAULT_VARIANT_NAME = "Padrão";

  private final ProductRepository products;
  private final VariantRepository variants;
  private final PackagingRepository packagings;
  private final CatalogBrandRepository brands;
  private final ApplicationEventPublisher events;

  // ---------------------------------------------------------------------------
  // Produto
  // ---------------------------------------------------------------------------

  @Transactional
  public ProductSummary register(RegisterProductCommand cmd) {
    requireNcmForGoods(cmd.classification(), cmd.ncm());
    rejectDuplicateSkuRoot(cmd.skuRoot());
    requireExistingBrandOrNull(cmd.brandId() == null ? null : cmd.brandId().value());

    Product saved =
        products.save(
            Product.register(
                cmd.skuRoot(),
                cmd.name(),
                cmd.description(),
                cmd.brandId() == null ? null : cmd.brandId().value(),
                cmd.classification(),
                cmd.ncm(),
                cmd.cest(),
                cmd.origin()));

    String variantCode =
        cmd.defaultVariantCode() == null ? DEFAULT_VARIANT_CODE : cmd.defaultVariantCode();
    Variant defaultVariant =
        variants.save(Variant.create(saved.id().value(), variantCode, DEFAULT_VARIANT_NAME, true));

    if (cmd.defaultPackagingSku() != null && !cmd.defaultPackagingSku().isBlank()) {
      rejectDuplicateSku(cmd.defaultPackagingSku());
      if (cmd.defaultPackagingBarcode() != null && !cmd.defaultPackagingBarcode().isBlank()) {
        rejectDuplicateBarcode(cmd.defaultPackagingBarcode());
      }
      Packaging defaultPackaging =
          packagings.save(
              Packaging.create(
                  defaultVariant.id().value(),
                  cmd.defaultPackagingSku(),
                  cmd.defaultPackagingBarcode(),
                  null,
                  null,
                  null,
                  true,
                  null));
      events.publishEvent(
          new PackagingCreatedEvent(
              defaultPackaging.id(),
              defaultVariant.id().value(),
              saved.id(),
              currentTenantId(),
              defaultPackaging.getSku(),
              defaultPackaging.getBarcode(),
              cmd.actor(),
              Instant.now()));
    }

    events.publishEvent(
        new ProductCreatedEvent(
            saved.id(),
            currentTenantId(),
            saved.getSkuRoot(),
            saved.getName(),
            saved.getClassification(),
            cmd.actor(),
            Instant.now()));

    log.info(
        "product registered id={} skuRoot={} actor={}",
        saved.id().value(),
        cmd.skuRoot(),
        cmd.actor().value());
    return ProductSummary.of(saved);
  }

  @Transactional
  public ProductSummary update(UpdateProductCommand cmd) {
    Product p = loadProduct(cmd.productId());
    requireExistingBrandOrNull(cmd.brandId() == null ? null : cmd.brandId().value());
    p.update(
        cmd.name(),
        cmd.description(),
        cmd.brandId() == null ? null : cmd.brandId().value(),
        cmd.ncm(),
        cmd.cest(),
        cmd.origin());
    if (p.getClassification() != Classification.SERVICO
        && (p.getNcm() == null || p.getNcm().isBlank())) {
      throw new InvalidProductDataException(
          "ncm-required", "NCM é obrigatório para esta classificação (RN-PRD-002).");
    }
    return ProductSummary.of(p);
  }

  @Transactional
  public ProductSummary deactivate(ProductId productId) {
    Product p = loadProduct(productId);
    p.deactivate();
    return ProductSummary.of(p);
  }

  @Transactional
  public ProductSummary reactivate(ProductId productId) {
    Product p = loadProduct(productId);
    p.reactivate();
    return ProductSummary.of(p);
  }

  @Transactional(readOnly = true)
  public ProductSummary findById(ProductId productId) {
    return ProductSummary.of(loadProduct(productId));
  }

  @Transactional(readOnly = true)
  public Page<ProductSummary> searchByName(String term, Pageable pageable) {
    return products.searchByName(term, pageable).map(ProductSummary::of);
  }

  @Transactional(readOnly = true)
  public Page<ProductSummary> list(Pageable pageable) {
    return products.findAll(pageable).map(ProductSummary::of);
  }

  // ---------------------------------------------------------------------------
  // Variant
  // ---------------------------------------------------------------------------

  @Transactional
  public VariantSummary addVariant(RegisterVariantCommand cmd) {
    loadProduct(cmd.productId()); // valida que produto existe
    if (variants.existsByProductIdAndCode(cmd.productId().value(), cmd.code())) {
      throw new InvalidProductDataException(
          "duplicate-variant-code", "Já existe variant com este code neste produto.");
    }
    if (cmd.asDefault()) {
      variants.clearDefaultForProduct(cmd.productId().value());
    }
    Variant v = Variant.create(cmd.productId().value(), cmd.code(), cmd.name(), cmd.asDefault());
    return VariantSummary.of(variants.save(v));
  }

  // ---------------------------------------------------------------------------
  // Packaging
  // ---------------------------------------------------------------------------

  @Transactional
  public PackagingSummary addPackaging(RegisterPackagingCommand cmd) {
    Variant variant =
        variants
            .findById(cmd.variantId())
            .orElseThrow(() -> new VariantNotFoundException(cmd.variantId()));
    rejectDuplicateSku(cmd.sku());
    if (cmd.barcode() != null && !cmd.barcode().isBlank()) {
      rejectDuplicateBarcode(cmd.barcode());
    }
    if (cmd.asDefault()) {
      packagings.clearDefaultForVariant(variant.id().value());
    }
    Packaging p =
        Packaging.create(
            variant.id().value(),
            cmd.sku(),
            cmd.barcode(),
            cmd.commercialUnitId(),
            cmd.fiscalUnitId(),
            cmd.conversionFactor(),
            cmd.asDefault(),
            cmd.weightKg());
    Packaging saved = packagings.save(p);

    events.publishEvent(
        new PackagingCreatedEvent(
            saved.id(),
            variant.id().value(),
            variant.productIdTyped(),
            currentTenantId(),
            saved.getSku(),
            saved.getBarcode(),
            cmd.actor(),
            Instant.now()));

    log.info(
        "packaging registered id={} sku={} variantId={} actor={}",
        saved.id().value(),
        cmd.sku(),
        variant.id().value(),
        cmd.actor().value());
    return PackagingSummary.of(saved);
  }

  @Transactional
  public PackagingSummary updatePackaging(UpdatePackagingCommand cmd) {
    Packaging p = loadPackaging(cmd.packagingId());
    if (cmd.barcode() != null
        && !cmd.barcode().isBlank()
        && !cmd.barcode().equals(p.getBarcode())) {
      rejectDuplicateBarcode(cmd.barcode());
    }
    p.update(
        cmd.barcode(),
        cmd.commercialUnitId(),
        cmd.fiscalUnitId(),
        cmd.conversionFactor(),
        cmd.weightKg());
    return PackagingSummary.of(p);
  }

  @Transactional
  public PackagingSummary markPackagingDefault(PackagingId packagingId) {
    Packaging p = loadPackaging(packagingId);
    if (!p.isDefault()) {
      packagings.clearDefaultForVariant(p.getVariantId());
      p.markDefault();
    }
    return PackagingSummary.of(p);
  }

  @Transactional
  public PackagingSummary deactivatePackaging(
      PackagingId packagingId, dev.dimi.erp.identidade.UserId actor) {
    Packaging p = loadPackaging(packagingId);
    p.deactivate();
    events.publishEvent(
        new PackagingDeactivatedEvent(p.id(), currentTenantId(), actor, Instant.now()));
    return PackagingSummary.of(p);
  }

  @Transactional(readOnly = true)
  public PackagingSummary findPackagingById(PackagingId packagingId) {
    return PackagingSummary.of(loadPackaging(packagingId));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private Product loadProduct(ProductId productId) {
    return products
        .findById(productId.value())
        .orElseThrow(() -> new ProductNotFoundException(productId));
  }

  private Packaging loadPackaging(PackagingId packagingId) {
    return packagings
        .findById(packagingId.value())
        .orElseThrow(() -> new PackagingNotFoundException(packagingId));
  }

  private void requireNcmForGoods(Classification c, String ncm) {
    if (c != Classification.SERVICO && (ncm == null || ncm.isBlank())) {
      throw new InvalidProductDataException(
          "ncm-required", "NCM é obrigatório para esta classificação (RN-PRD-002).");
    }
  }

  private void rejectDuplicateSkuRoot(String skuRoot) {
    if (products.existsBySkuRoot(skuRoot)) {
      throw new DuplicateSkuException(skuRoot);
    }
  }

  private void rejectDuplicateSku(String sku) {
    if (packagings.existsBySku(sku)) {
      throw new DuplicateSkuException(sku);
    }
  }

  private void rejectDuplicateBarcode(String barcode) {
    if (packagings.existsByBarcode(barcode)) {
      throw new DuplicateBarcodeException(barcode);
    }
  }

  private void requireExistingBrandOrNull(UUID brandId) {
    if (brandId != null && !brands.existsById(brandId)) {
      throw new BrandNotFoundException(new dev.dimi.erp.produto.BrandId(brandId));
    }
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
