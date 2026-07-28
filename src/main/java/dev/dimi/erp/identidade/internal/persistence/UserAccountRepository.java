package dev.dimi.erp.identidade.internal.persistence;

import dev.dimi.erp.identidade.internal.domain.UserAccount;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

  @Query("SELECT u FROM UserAccount u WHERE LOWER(u.username) = LOWER(:username)")
  Optional<UserAccount> findByUsernameIgnoreCase(@Param("username") String username);

  @Query("SELECT u FROM UserAccount u WHERE LOWER(u.email) = LOWER(:email)")
  Optional<UserAccount> findByEmailIgnoreCase(@Param("email") String email);

  /** Busca por username OU email (case-insensitive). Usado no login. */
  @Query(
      """
      SELECT u FROM UserAccount u
      WHERE LOWER(u.username) = LOWER(:identifier)
         OR LOWER(u.email) = LOWER(:identifier)
      """)
  Optional<UserAccount> findByUsernameOrEmail(@Param("identifier") String identifier);

  @Query("SELECT COUNT(u) > 0 FROM UserAccount u WHERE LOWER(u.username) = LOWER(:username)")
  boolean existsByUsernameIgnoreCase(@Param("username") String username);

  @Query("SELECT COUNT(u) > 0 FROM UserAccount u WHERE LOWER(u.email) = LOWER(:email)")
  boolean existsByEmailIgnoreCase(@Param("email") String email);

  @Query("SELECT u FROM UserAccount u ORDER BY LOWER(u.username) ASC")
  Page<UserAccount> findAllOrderedByUsername(Pageable pageable);
}
