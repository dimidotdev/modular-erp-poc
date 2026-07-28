package dev.dimi.erp.identidade.internal.persistence;

import dev.dimi.erp.identidade.internal.domain.LoginAttempt;
import dev.dimi.erp.identidade.internal.domain.LoginAttemptId;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LoginAttemptRepository extends JpaRepository<LoginAttempt, LoginAttemptId> {

  @Query(
      """
      SELECT COUNT(a) FROM LoginAttempt a
      WHERE LOWER(a.username) = LOWER(:username)
        AND a.success = false
        AND a.id.attemptedAt >= :since
      """)
  long countFailuresByUsernameSince(
      @Param("username") String username, @Param("since") Instant since);

  @Query(
      """
      SELECT COUNT(a) FROM LoginAttempt a
      WHERE a.ip = :ip
        AND a.success = false
        AND a.id.attemptedAt >= :since
      """)
  long countFailuresByIpSince(@Param("ip") String ip, @Param("since") Instant since);
}
