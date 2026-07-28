package dev.dimi.erp.produto.internal.persistence;

import dev.dimi.erp.produto.internal.domain.Product;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, UUID> {

  Optional<Product> findBySkuRoot(String skuRoot);

  boolean existsBySkuRoot(String skuRoot);

  @Query(
      """
      SELECT p FROM Product p
      WHERE p.active = true
        AND LOWER(p.name) LIKE LOWER(CONCAT('%', :term, '%'))
      """)
  Page<Product> searchByName(@Param("term") String term, Pageable pageable);
}
