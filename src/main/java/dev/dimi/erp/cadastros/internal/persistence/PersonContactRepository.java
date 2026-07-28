package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.internal.domain.PersonContact;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PersonContactRepository extends JpaRepository<PersonContact, UUID> {

  List<PersonContact> findByPersonId(UUID personId);

  Optional<PersonContact> findByPersonIdAndPrimaryContactTrue(UUID personId);

  void deleteByPersonId(UUID personId);
}
