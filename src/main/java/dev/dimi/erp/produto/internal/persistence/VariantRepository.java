package dev.dimi.erp.produto.internal.persistence;

import dev.dimi.erp.produto.internal.domain.Variant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface VariantRepository extends JpaRepository<Variant, UUID> {

  List<Variant> findByProductId(UUID productId);

  Optional<Variant> findByProductIdAndCode(UUID productId, String code);

  Optional<Variant> findByProductIdAndIsDefaultTrue(UUID productId);

  boolean existsByProductIdAndCode(UUID productId, String code);

  /**
   * Limpa o flag {@code is_default} via UPDATE direto no banco — necessário para o partial index
   * {@code uq_variant_default_per_product}, que Postgres avalia statement-a-statement. Sem isso,
   * Hibernate pode ordenar o INSERT do novo default ANTES do UPDATE do antigo no mesmo flush e
   * violar a constraint.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update Variant v set v.isDefault = false "
          + "where v.productId = :productId and v.isDefault = true")
  int clearDefaultForProduct(@Param("productId") UUID productId);
}
