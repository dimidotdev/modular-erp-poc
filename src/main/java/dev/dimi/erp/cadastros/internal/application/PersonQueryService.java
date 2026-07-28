package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.Modality;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.PersonQueryPort;
import dev.dimi.erp.cadastros.PersonView;
import dev.dimi.erp.cadastros.internal.domain.Person;
import dev.dimi.erp.cadastros.internal.persistence.PersonModalityRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonRepository;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter interno que expõe a porta {@link PersonQueryPort}. Mapeia {@link Person} (entidade
 * interna) para {@link PersonView} (record público) — outros módulos só enxergam a interface.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class PersonQueryService implements PersonQueryPort {

  private final PersonRepository persons;
  private final PersonModalityRepository modalities;

  @Override
  public Optional<PersonView> findById(PersonId id) {
    return persons.findById(id.value()).map(this::toView);
  }

  @Override
  public boolean exists(PersonId id) {
    return persons.existsById(id.value());
  }

  @Override
  public boolean isActive(PersonId id) {
    return persons.findById(id.value()).map(p -> p.isActive() && !p.isAnonymized()).orElse(false);
  }

  private PersonView toView(Person p) {
    Set<Modality> active =
        modalities.findByPersonId(p.id().value()).stream()
            .filter(m -> m.isActive())
            .map(m -> m.getId().getModality())
            .collect(Collectors.toUnmodifiableSet());
    return new PersonView(
        p.id(),
        p.getType(),
        p.getLegalName(),
        p.getTradeName(),
        documentOf(p),
        p.isActive(),
        p.isAnonymized(),
        active);
  }

  private static String documentOf(Person p) {
    return switch (p.getType()) {
      case PF -> p.getCpf();
      case PJ -> p.getCnpj();
    };
  }
}
