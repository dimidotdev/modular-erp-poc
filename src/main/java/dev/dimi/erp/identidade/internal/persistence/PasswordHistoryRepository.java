package dev.dimi.erp.identidade.internal.persistence;

import dev.dimi.erp.identidade.internal.domain.PasswordHistoryEntry;
import dev.dimi.erp.identidade.internal.domain.PasswordHistoryId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PasswordHistoryRepository
    extends JpaRepository<PasswordHistoryEntry, PasswordHistoryId> {

  /** Últimas N senhas do usuário, ordem decrescente por data de criação. */
  @Query(
      """
      SELECT h FROM PasswordHistoryEntry h
      WHERE h.id.userId = :userId
      ORDER BY h.id.createdAt DESC
      """)
  List<PasswordHistoryEntry> findRecentByUserId(@Param("userId") UUID userId);
}
