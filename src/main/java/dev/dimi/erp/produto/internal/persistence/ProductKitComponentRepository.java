package dev.dimi.erp.produto.internal.persistence;

import dev.dimi.erp.produto.internal.domain.ProductKitComponent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductKitComponentRepository extends JpaRepository<ProductKitComponent, UUID> {

  List<ProductKitComponent> findByKitPackagingId(UUID kitPackagingId);

  List<ProductKitComponent> findByComponentPackagingId(UUID componentPackagingId);

  void deleteByKitPackagingId(UUID kitPackagingId);
}
