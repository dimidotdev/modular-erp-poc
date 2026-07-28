package dev.dimi.erp.contratos.internal.application;

import dev.dimi.erp.contratos.Recurrence;
import dev.dimi.erp.contratos.internal.domain.BillingCycle;
import dev.dimi.erp.contratos.internal.domain.Contract;
import dev.dimi.erp.contratos.internal.persistence.BillingCycleRepository;
import dev.dimi.erp.contratos.internal.persistence.ContractRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Materializa próximos {@link BillingCycle}s {@code PENDING} para contratos {@code ACTIVE} dentro
 * de uma janela de lookahead (RN-CTR-008 — 7 dias). Idempotente via UNIQUE {@code (contract_id,
 * competence_month)} no banco — checa via {@code findByContractIdAndCompetenceMonth} antes de
 * criar.
 *
 * <p>"Competência" aqui = primeiro dia do mês (ou intervalo equivalente, conforme {@link
 * Recurrence}). O {@code dueDate} é calculado a partir de {@code billingDay} do contrato, clampado
 * ao último dia do mês quando necessário (ex.: billingDay=31 em fevereiro → dia 28/29).
 *
 * <p>O loop interno caminha em passos de recorrência a partir de {@code contract.startDate} até
 * {@code today + lookahead} ou {@code contract.endDate} (o que vier antes), aplicando regra "não
 * agendar competências anteriores ao mês corrente" — RN-CTR-009: ciclos passados sem geração viram
 * dívida técnica do tenant, não são auto-geradas retroativamente pelo job.
 */
@Service
public class ScheduleBillingCyclesService {

  private static final Logger log = LoggerFactory.getLogger(ScheduleBillingCyclesService.class);

  /** Lookahead window in days (RN-CTR-008). */
  private static final int LOOKAHEAD_DAYS = 7;

  private final ContractRepository contracts;
  private final BillingCycleRepository cycles;

  public ScheduleBillingCyclesService(ContractRepository contracts, BillingCycleRepository cycles) {
    this.contracts = contracts;
    this.cycles = cycles;
  }

  /**
   * Para cada contrato {@code ACTIVE}, materializa ciclos {@code PENDING} para competências
   * esperadas até {@code today + LOOKAHEAD_DAYS}. Retorna o número de ciclos criados.
   */
  @Transactional
  public int scheduleUpcoming(LocalDate today) {
    LocalDate horizon = today.plusDays(LOOKAHEAD_DAYS);
    List<Contract> active = contracts.findByStatusActive();
    int created = 0;
    for (Contract c : active) {
      List<LocalDate> expected = expectedCompetencesBetween(c, today, horizon);
      for (LocalDate competence : expected) {
        if (cycles.findByContractIdAndCompetenceMonth(c.getId(), competence).isPresent()) {
          continue;
        }
        LocalDate due = computeDueDate(competence, c.getBillingDay());
        cycles.save(BillingCycle.schedule(c.getId(), competence, due, c.getBaseValue()));
        created++;
      }
    }
    log.info("Scheduled {} new billing cycles (horizon={})", created, horizon);
    return created;
  }

  /**
   * Calcula as competências esperadas para o contrato dentro de {@code [from, to]}, partindo de
   * {@code contract.startDate} (normalizada ao dia 1 do mês) e caminhando em passos de {@link
   * Recurrence}. Competências anteriores a {@code from.withDayOfMonth(1)} são puladas — não
   * agendamos retroativamente.
   */
  private List<LocalDate> expectedCompetencesBetween(Contract c, LocalDate from, LocalDate to) {
    LocalDate start = c.getStartDate();
    LocalDate end = c.getEndDate() == null ? to : c.getEndDate();
    LocalDate fromCompetence = from.withDayOfMonth(1);
    List<LocalDate> out = new ArrayList<>();
    LocalDate cursor = start.withDayOfMonth(1);
    while (!cursor.isAfter(to) && !cursor.isAfter(end)) {
      if (!cursor.isBefore(fromCompetence)) {
        out.add(cursor);
      }
      cursor = nextCompetence(cursor, c.getRecurrence());
    }
    return out;
  }

  private LocalDate nextCompetence(LocalDate current, Recurrence r) {
    return switch (r) {
      case MONTHLY -> current.plusMonths(1);
      case BIWEEKLY -> current.plusDays(14);
      case QUARTERLY -> current.plusMonths(3);
      case SEMIANNUAL -> current.plusMonths(6);
      case ANNUAL -> current.plusYears(1);
    };
  }

  private LocalDate computeDueDate(LocalDate competence, int billingDay) {
    int dayInMonth = Math.min(billingDay, competence.lengthOfMonth());
    return competence.withDayOfMonth(dayInMonth);
  }
}
