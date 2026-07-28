package dev.dimi.erp.produto.internal.persistence;

import dev.dimi.erp.produto.internal.domain.AttributeTemplate;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AttributeTemplateRepository extends JpaRepository<AttributeTemplate, UUID> {

  Optional<AttributeTemplate> findByCode(String code);

  boolean existsByCode(String code);
}
