package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.cadastros.internal.domain.Person;
import dev.dimi.erp.cadastros.internal.persistence.EmployeeRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonAddressRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonContactRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonModalityRepository;
import dev.dimi.erp.cadastros.internal.persistence.PersonRepository;
import dev.dimi.erp.identidade.UserId;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Geração de export consolidado de dados pessoais (RN-CAD-016 / LGPD Art. 18 V).
 *
 * <p>v1 cobre o que o módulo cadastros conhece sobre a pessoa: identificação, modalidades,
 * endereços, contatos e vínculos como funcionário. Slices futuros (vendas, MVF, OS, contratos)
 * complementarão via Query Ports próprias quando o endpoint consumidor for migrado para chamar
 * cross-module.
 *
 * <p>Operação considerada sensível (PII bruta no payload): o caller (controller) é responsável por
 * exigir step-up via {@code StepUpPolicy.requireFresh} antes de invocar. Audit log da emissão fica
 * sob responsabilidade do módulo Auditoria (slice 11) — aqui logamos no app log padrão.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PersonalDataExportService {

  private final PersonRepository persons;
  private final PersonModalityRepository modalities;
  private final PersonAddressRepository addresses;
  private final PersonContactRepository contacts;
  private final EmployeeRepository employees;

  @Transactional(readOnly = true)
  public PersonalDataExport export(PersonId personId, UserId actor) {
    Person person =
        persons.findById(personId.value()).orElseThrow(() -> new PersonNotFoundException(personId));

    PersonSummary personSummary =
        PersonSummary.of(person, modalities.findByPersonId(personId.value()));

    List<PersonAddressSummary> addressSummaries =
        addresses.findByPersonId(personId.value()).stream().map(PersonAddressSummary::of).toList();
    List<PersonContactSummary> contactSummaries =
        contacts.findByPersonId(personId.value()).stream().map(PersonContactSummary::of).toList();
    List<EmployeeSummary> employmentSummaries =
        employees.findByPersonId(personId.value()).stream().map(EmployeeSummary::of).toList();

    log.info(
        "personal-data-export gerado personId={} actor={} addresses={} contacts={} employments={}",
        personId.value(),
        actor.value(),
        addressSummaries.size(),
        contactSummaries.size(),
        employmentSummaries.size());

    return new PersonalDataExport(
        personSummary,
        addressSummaries,
        contactSummaries,
        employmentSummaries,
        actor,
        Instant.now());
  }
}
