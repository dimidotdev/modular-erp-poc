package dev.dimi.erp.auditoria.internal.persistence;

import dev.dimi.erp.auditoria.AuditCategory;
import dev.dimi.erp.auditoria.internal.domain.RetentionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório das políticas de retenção. PK é o enum {@link AuditCategory} (mapeado via {@code
 * EnumType.STRING}). {@link JpaRepository#findAll()} retorna a lista completa — usada pelo job para
 * iterar categoria a categoria.
 */
public interface RetentionPolicyRepository extends JpaRepository<RetentionPolicy, AuditCategory> {}
