package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.internal.domain.BaseCatalog;
import dev.dimi.erp.cadastros.internal.domain.CatalogAddressType;
import dev.dimi.erp.cadastros.internal.domain.CatalogContactType;
import dev.dimi.erp.cadastros.internal.domain.CatalogDepartment;
import dev.dimi.erp.cadastros.internal.domain.CatalogJobTitle;
import dev.dimi.erp.cadastros.internal.domain.CatalogMeasurementUnit;
import dev.dimi.erp.cadastros.internal.domain.CatalogRegion;
import dev.dimi.erp.cadastros.internal.domain.CatalogSegment;
import dev.dimi.erp.cadastros.internal.persistence.CatalogAddressTypeRepository;
import dev.dimi.erp.cadastros.internal.persistence.CatalogContactTypeRepository;
import dev.dimi.erp.cadastros.internal.persistence.CatalogDepartmentRepository;
import dev.dimi.erp.cadastros.internal.persistence.CatalogJobTitleRepository;
import dev.dimi.erp.cadastros.internal.persistence.CatalogMeasurementUnitRepository;
import dev.dimi.erp.cadastros.internal.persistence.CatalogRegionRepository;
import dev.dimi.erp.cadastros.internal.persistence.CatalogSegmentRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações genéricas sobre catálogos auxiliares. Despacha pelo {@link CatalogType} para o
 * repositório correto.
 *
 * <p>RN-CAD-013: entradas {@code system=true} (seed) não podem ser editadas/desativadas. Tenant
 * pode criar entradas custom livremente.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CatalogService {

  private final CatalogSegmentRepository segments;
  private final CatalogRegionRepository regions;
  private final CatalogAddressTypeRepository addressTypes;
  private final CatalogContactTypeRepository contactTypes;
  private final CatalogJobTitleRepository jobTitles;
  private final CatalogDepartmentRepository departments;
  private final CatalogMeasurementUnitRepository measurementUnits;

  // ---------------------------------------------------------------------------
  // Queries
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public List<CatalogEntrySummary> list(CatalogType type) {
    return repositoryOf(type).findAll().stream().map(CatalogEntrySummary::of).toList();
  }

  @Transactional(readOnly = true)
  public CatalogEntrySummary findById(CatalogType type, UUID id) {
    BaseCatalog c =
        repositoryOf(type)
            .findById(id)
            .orElseThrow(() -> new CatalogEntryNotFoundException(type, id));
    return CatalogEntrySummary.of(c);
  }

  // ---------------------------------------------------------------------------
  // Comandos
  // ---------------------------------------------------------------------------

  @Transactional
  public CatalogEntrySummary create(CatalogType type, String code, String name) {
    if (existsByCode(type, code)) {
      throw new CatalogEntryAlreadyExistsException(type, code);
    }
    BaseCatalog saved = persistNew(type, code, name);
    log.info("catalogo entry criado type={} code={} id={}", type.slug(), code, saved.getId());
    return CatalogEntrySummary.of(saved);
  }

  @Transactional
  public CatalogEntrySummary rename(CatalogType type, UUID id, String newName) {
    BaseCatalog c =
        repositoryOf(type)
            .findById(id)
            .orElseThrow(() -> new CatalogEntryNotFoundException(type, id));
    if (c.isSystem()) {
      throw new SystemCatalogEntryImmutableException(type, id);
    }
    c.rename(newName);
    log.info("catalogo entry renomeado type={} id={} newName={}", type.slug(), id, newName);
    return CatalogEntrySummary.of(c);
  }

  @Transactional
  public CatalogEntrySummary deactivate(CatalogType type, UUID id) {
    BaseCatalog c =
        repositoryOf(type)
            .findById(id)
            .orElseThrow(() -> new CatalogEntryNotFoundException(type, id));
    if (c.isSystem()) {
      throw new SystemCatalogEntryImmutableException(type, id);
    }
    c.deactivate();
    log.info("catalogo entry desativado type={} id={}", type.slug(), id);
    return CatalogEntrySummary.of(c);
  }

  @Transactional
  public CatalogEntrySummary reactivate(CatalogType type, UUID id) {
    BaseCatalog c =
        repositoryOf(type)
            .findById(id)
            .orElseThrow(() -> new CatalogEntryNotFoundException(type, id));
    c.reactivate();
    log.info("catalogo entry reativado type={} id={}", type.slug(), id);
    return CatalogEntrySummary.of(c);
  }

  // ---------------------------------------------------------------------------
  // Dispatch
  // ---------------------------------------------------------------------------

  private JpaRepository<? extends BaseCatalog, UUID> repositoryOf(CatalogType type) {
    return switch (type) {
      case SEGMENT -> segments;
      case REGION -> regions;
      case ADDRESS_TYPE -> addressTypes;
      case CONTACT_TYPE -> contactTypes;
      case JOB_TITLE -> jobTitles;
      case DEPARTMENT -> departments;
      case MEASUREMENT_UNIT -> measurementUnits;
    };
  }

  private boolean existsByCode(CatalogType type, String code) {
    return switch (type) {
      case SEGMENT -> segments.existsByCode(code);
      case REGION -> regions.existsByCode(code);
      case ADDRESS_TYPE -> addressTypes.existsByCode(code);
      case CONTACT_TYPE -> contactTypes.existsByCode(code);
      case JOB_TITLE -> jobTitles.existsByCode(code);
      case DEPARTMENT -> departments.existsByCode(code);
      case MEASUREMENT_UNIT -> measurementUnits.existsByCode(code);
    };
  }

  private BaseCatalog persistNew(CatalogType type, String code, String name) {
    return switch (type) {
      case SEGMENT -> segments.save(CatalogSegment.create(code, name));
      case REGION -> regions.save(CatalogRegion.create(code, name));
      case ADDRESS_TYPE -> addressTypes.save(CatalogAddressType.create(code, name));
      case CONTACT_TYPE -> contactTypes.save(CatalogContactType.create(code, name));
      case JOB_TITLE -> jobTitles.save(CatalogJobTitle.create(code, name));
      case DEPARTMENT -> departments.save(CatalogDepartment.create(code, name));
      case MEASUREMENT_UNIT -> measurementUnits.save(CatalogMeasurementUnit.create(code, name));
    };
  }
}
