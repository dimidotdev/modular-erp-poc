package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.PersonAddress;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonAddressRepository extends JpaRepository<PersonAddress, UUID> {

  List<PersonAddress> findByPersonId(UUID personId);

  Optional<PersonAddress> findByPersonIdAndDefaultAddressTrue(UUID personId);

  void deleteByPersonId(UUID personId);

  /**
   * Limpa o flag {@code is_default} via UPDATE direto no banco — evita que Hibernate ordene o
   * UPDATE do antigo padrão e do novo padrão arbitrariamente no mesmo flush, o que viola o partial
   * index {@code uq_person_default_address}.
   */
  @Modifying(flushAutomatically = true)
  @Query(
      "update PersonAddress a set a.defaultAddress = false "
          + "where a.personId = :personId and a.defaultAddress = true")
  int clearDefaultForPerson(@Param("personId") UUID personId);
}
