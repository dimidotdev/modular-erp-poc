package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.identidade.UserId;
import java.time.Instant;
import java.util.List;

/**
 * Snapshot consolidado dos dados pessoais que o tenant trata sobre uma {@code Person}, gerado em
 * resposta a uma solicitação de portabilidade LGPD (Art. 18 V).
 *
 * <p>Cobre o que o módulo cadastros conhece. Quando outros módulos (vendas, MVF, OS, contratos)
 * existirem, eles plugarão seus próprios {@code *DataExport} via Query Port para complementar este
 * payload — o consumer final ({@code GET .../personal-data-export}) então agrega tudo.
 */
public record PersonalDataExport(
    PersonSummary person,
    List<PersonAddressSummary> addresses,
    List<PersonContactSummary> contacts,
    List<EmployeeSummary> employments,
    UserId exportedBy,
    Instant exportedAt) {}
