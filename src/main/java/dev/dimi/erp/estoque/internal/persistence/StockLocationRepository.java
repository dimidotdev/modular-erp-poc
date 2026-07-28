package dev.dimi.erp.estoque.internal.persistence;

import dev.dimi.erp.estoque.internal.domain.StockLocation;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StockLocationRepository extends JpaRepository<StockLocation, UUID> {

  List<StockLocation> findByBranchId(UUID branchId);

  Optional<StockLocation> findByBranchIdAndCode(UUID branchId, String code);

  Optional<StockLocation> findByBranchIdAndIsDefaultTrue(UUID branchId);

  boolean existsByBranchIdAndCode(UUID branchId, String code);

  /**
   * UPDATE direto para limpar {@code is_default} antes do INSERT do novo padrão — mesma razão das
   * variants/packagings do produto: partial index {@code uq_location_default_per_branch} é avaliado
   * por statement, então Hibernate-managed updates podem violar o índice em flush.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update StockLocation l set l.isDefault = false "
          + "where l.branchId = :branchId and l.isDefault = true")
  int clearDefaultForBranch(@Param("branchId") UUID branchId);
}
