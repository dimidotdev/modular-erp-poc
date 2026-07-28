package dev.dimi.erp.auditoria.internal.persistence;

import dev.dimi.erp.auditoria.internal.domain.RetentionRun;
import dev.dimi.erp.auditoria.internal.domain.RunStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repositório da trilha de execuções do job de retenção.
 *
 * <p>{@link #findTop10ByOrderByStartedAtDesc()} alimenta dashboards administrativos; {@link
 * #existsByStatus(RunStatus)} é usado pelo health-check para detectar execuções travadas (RUNNING
 * há mais que o timeout do ShedLock).
 */
public interface RetentionRunRepository extends JpaRepository<RetentionRun, UUID> {

  List<RetentionRun> findTop10ByOrderByStartedAtDesc();

  boolean existsByStatus(RunStatus status);
}
