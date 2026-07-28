package dev.dimi.erp.cadastros.internal.persistence;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.internal.domain.PersonModalityAssignment;
import dev.dimi.erp.cadastros.internal.domain.PersonModalityId;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PersonModalityRepository
    extends JpaRepository<PersonModalityAssignment, PersonModalityId> {

  @Query(
      """
      SELECT m FROM PersonModalityAssignment m
      WHERE m.id.personId = :personId
      """)
  List<PersonModalityAssignment> findByPersonId(@Param("personId") UUID personId);

  @Query(
      """
      SELECT m FROM PersonModalityAssignment m
      WHERE m.id.modality = :modality
        AND m.active = true
      """)
  List<PersonModalityAssignment> findActiveByModality(@Param("modality") Modality modality);
}
