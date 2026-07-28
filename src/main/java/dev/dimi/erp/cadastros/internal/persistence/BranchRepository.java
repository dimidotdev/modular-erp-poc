package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.Branch;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BranchRepository extends JpaRepository<Branch, UUID> {

  Optional<Branch> findByCode(String code);

  Optional<Branch> findByMainTrue();

  List<Branch> findByActiveTrue();

  boolean existsByCode(String code);

  /**
   * Desmarca a matriz atual via UPDATE direto — evita que Hibernate ordene o UPDATE do antigo
   * matriz e do novo matriz arbitrariamente no mesmo flush, o que violaria o partial index {@code
   * uq_branch_main} (mesmo padrão de {@link PersonAddressRepository#clearDefaultForPerson}).
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update Branch b set b.main = false "
          + "where b.main = true and (:keepId is null or b.id <> :keepId)")
  int clearCurrentMain(@Param("keepId") UUID keepId);
}
