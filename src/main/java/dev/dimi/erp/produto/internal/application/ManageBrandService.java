package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.internal.domain.CatalogBrand;
import dev.dimi.erp.produto.internal.persistence.CatalogBrandRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * CRUD do catálogo {@code catalog_brand}. Operações simples — sem eventos de domínio (mudança em
 * marca não muda PII nem dispara audit relevante a outros módulos no v1). Auditoria do tenant
 * captura via interceptor padrão se configurado.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManageBrandService {

  private final CatalogBrandRepository brands;

  @Transactional
  public BrandSummary register(RegisterBrandCommand cmd) {
    if (brands.existsByCode(cmd.code())) {
      throw new InvalidProductDataException(
          "duplicate-brand-code", "Já existe marca com o code informado.");
    }
    CatalogBrand saved = brands.save(CatalogBrand.create(cmd.code(), cmd.name()));
    log.info(
        "brand registered id={} code={} actor={}",
        saved.id().value(),
        cmd.code(),
        cmd.actor().value());
    return BrandSummary.of(saved);
  }

  @Transactional
  public BrandSummary rename(BrandId brandId, String name) {
    CatalogBrand b = load(brandId);
    b.rename(name);
    return BrandSummary.of(b);
  }

  @Transactional
  public BrandSummary deactivate(BrandId brandId) {
    CatalogBrand b = load(brandId);
    b.deactivate();
    return BrandSummary.of(b);
  }

  @Transactional
  public BrandSummary reactivate(BrandId brandId) {
    CatalogBrand b = load(brandId);
    b.reactivate();
    return BrandSummary.of(b);
  }

  @Transactional(readOnly = true)
  public BrandSummary findById(BrandId brandId) {
    return BrandSummary.of(load(brandId));
  }

  @Transactional(readOnly = true)
  public List<BrandSummary> list() {
    return brands.findAll().stream().map(BrandSummary::of).toList();
  }

  private CatalogBrand load(BrandId brandId) {
    return brands.findById(brandId.value()).orElseThrow(() -> new BrandNotFoundException(brandId));
  }
}
