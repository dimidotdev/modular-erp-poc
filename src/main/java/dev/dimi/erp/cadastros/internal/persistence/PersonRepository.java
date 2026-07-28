package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.Person;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonRepository extends JpaRepository<Person, UUID> {

  Optional<Person> findByCpf(String cpf);

  Optional<Person> findByCnpj(String cnpj);

  boolean existsByCpf(String cpf);

  boolean existsByCnpj(String cnpj);

  @Query(
      """
      SELECT p FROM Person p
      WHERE p.active = true
        AND p.anonymized = false
        AND LOWER(p.legalName) LIKE LOWER(CONCAT('%', :term, '%'))
      """)
  Page<Person> searchByLegalName(@Param("term") String term, Pageable pageable);
}
