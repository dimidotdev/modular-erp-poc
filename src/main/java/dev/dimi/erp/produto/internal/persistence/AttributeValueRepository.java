package dev.dimi.erp.produto.internal.persistence;

import dev.dimi.erp.produto.internal.domain.AttributeValue;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttributeValueRepository extends JpaRepository<AttributeValue, UUID> {

  List<AttributeValue> findByProductId(UUID productId);

  List<AttributeValue> findByVariantId(UUID variantId);

  Optional<AttributeValue> findByProductIdAndTemplateId(UUID productId, UUID templateId);

  Optional<AttributeValue> findByVariantIdAndTemplateId(UUID variantId, UUID templateId);
}
