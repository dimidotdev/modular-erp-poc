package dev.dimi.erp.produto.internal.persistence;

import dev.dimi.erp.produto.internal.domain.Packaging;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PackagingRepository extends JpaRepository<Packaging, UUID> {

  List<Packaging> findByVariantId(UUID variantId);

  Optional<Packaging> findByVariantIdAndIsDefaultTrue(UUID variantId);

  Optional<Packaging> findBySku(String sku);

  Optional<Packaging> findByBarcode(String barcode);

  boolean existsBySku(String sku);

  boolean existsByBarcode(String barcode);

  /**
   * UPDATE direto para limpar {@code is_default} antes do INSERT do novo padrão — mesma razão de
   * {@link VariantRepository#clearDefaultForProduct}: partial index é avaliado por statement.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update Packaging p set p.isDefault = false "
          + "where p.variantId = :variantId and p.isDefault = true")
  int clearDefaultForVariant(@Param("variantId") UUID variantId);
}
