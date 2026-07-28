package dev.dimi.erp.auditoria.internal.application;

import dev.dimi.erp.auditoria.RetentionAction;
import dev.dimi.erp.auditoria.internal.domain.RetentionPolicy;
import dev.dimi.erp.auditoria.internal.domain.RetentionRun;
import dev.dimi.erp.auditoria.internal.persistence.RetentionPolicyRepository;
import dev.dimi.erp.auditoria.internal.persistence.RetentionRunRepository;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Executa a retenção configurada por categoria em {@code audit.retention_policy}: para cada linha,
 * calcula {@code cutoff = now() - retainDays} e aplica {@link RetentionAction#PURGE} (DELETE) ou
 * {@link RetentionAction#ANONYMIZE} (UPDATE com payload anonimizado).
 *
 * <p><strong>V1 SIMPLIFICAÇÃO</strong>: a abordagem ideal para PURGE em grandes volumes é dropar
 * partições inteiras cujo {@code upper_bound} caiu além do cutoff — mas exige listar partições via
 * {@code pg_inherits} e materializar lógica complexa de "partição inteira < cutoff?". Em V1, PURGE
 * é apenas {@code DELETE FROM audit.audit_event WHERE event_category = ? AND occurred_at < ?}. A
 * janela rolante de partições antigas é responsabilidade isolada de {@link
 * PartitionMaintenanceService#dropPartitionsBefore(java.time.LocalDate)}, chamado separadamente
 * pelo job de manutenção (e é onde ganha-se a vantagem operacional de DROP vs DELETE).
 *
 * <p>Estratégia transacional:
 *
 * <ul>
 *   <li>{@code runRetention()} <strong>sem</strong> {@code @Transactional} na superfície — cada
 *       step (criar run, processar uma política, marcar sucesso/falha) abre sua própria transação
 *       curta via {@link TransactionTemplate}.
 *   <li>Evita locks de horas em {@code audit.audit_event} quando o volume é alto.
 *   <li>Falha de uma política não derruba as outras: try/catch por iteração; ao final, se {@code
 *       erroAcumulado != null}, marca o run como FAILED com a primeira mensagem.
 * </ul>
 *
 * <p>Concorrência distribuída fica a cargo do caller (job com {@code @SchedulerLock}); este service
 * é puro orquestrador.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditRetentionService {

  private static final String ANONYMIZED_PAYLOAD = "{\"anonymized\": true}";

  private final RetentionPolicyRepository policies;
  private final RetentionRunRepository runs;
  private final JdbcTemplate jdbc;
  private final TransactionTemplate tx;

  /**
   * Resultado consolidado de uma execução: contagens cumulativas e tempo de parede. {@code
   * partsDropped} fica em 0 nesta camada — partições são manejadas por {@link
   * PartitionMaintenanceService}; o campo existe para compatibilidade com o futuro orquestrador
   * único.
   */
  public record RetentionResult(long purged, long anonymized, int partsDropped, Duration elapsed) {}

  /**
   * Executa a retenção para todas as políticas registradas. Retorna o agregado das contagens.
   *
   * <p>Idempotente em essência: rodar duas vezes em sequência não dobra contagens — a segunda
   * execução vê os dados já anonimizados/removidos.
   */
  public RetentionResult runRetention() {
    Instant startWall = Instant.now();
    RetentionRun run = tx.execute(s -> Objects.requireNonNull(runs.save(RetentionRun.start())));
    if (run == null) {
      // TransactionTemplate.execute só retorna null se o callback retornar null — nosso save não
      // retorna null. Defensivo.
      throw new IllegalStateException("Falha criando RetentionRun");
    }
    final UUID runId = Objects.requireNonNull(run.getId(), "RetentionRun.id obrigatório");

    long totalPurged = 0L;
    long totalAnonymized = 0L;
    String firstError = null;

    List<RetentionPolicy> all = tx.execute(s -> policies.findAll());
    if (all == null) {
      all = List.of();
    }
    for (RetentionPolicy policy : all) {
      try {
        Instant cutoff = Instant.now().minus(policy.getRetainDays(), ChronoUnit.DAYS);
        if (policy.getActionAtEol() == RetentionAction.PURGE) {
          long deleted = purge(policy.getCategory().name(), cutoff);
          totalPurged += deleted;
          log.info(
              "AuditRetention PURGE category={} cutoff={} deleted={}",
              policy.getCategory(),
              cutoff,
              deleted);
        } else {
          long anonymized = anonymize(policy.getCategory().name(), cutoff);
          totalAnonymized += anonymized;
          log.info(
              "AuditRetention ANONYMIZE category={} cutoff={} updated={}",
              policy.getCategory(),
              cutoff,
              anonymized);
        }
      } catch (RuntimeException e) {
        if (firstError == null) {
          firstError = "category=" + policy.getCategory() + ": " + e.getMessage();
        }
        log.error(
            "AuditRetention falhou category={} — seguindo com próxima", policy.getCategory(), e);
      }
    }

    Duration elapsed = Duration.between(startWall, Instant.now());
    final long purgedFinal = totalPurged;
    final long anonymizedFinal = totalAnonymized;
    final String errorFinal = firstError;
    tx.executeWithoutResult(
        s -> {
          // Reattach pra mexer no run dentro da nova tx
          RetentionRun fresh = runs.findById(runId).orElseThrow();
          if (errorFinal == null) {
            fresh.markSuccess(purgedFinal, anonymizedFinal, 0, 0);
          } else {
            fresh.markFailed(errorFinal);
          }
          runs.save(fresh);
        });

    return new RetentionResult(totalPurged, totalAnonymized, 0, elapsed);
  }

  /**
   * Executa o DELETE em sua própria transação. Postgres permite DELETE em tabela particionada — o
   * planner descarta partições fora do range de {@code occurred_at < cutoff}.
   */
  private long purge(String category, Instant cutoff) {
    Long result =
        tx.execute(
            s ->
                (long)
                    jdbc.update(
                        "DELETE FROM audit.audit_event "
                            + "WHERE event_category = ? AND occurred_at < ?",
                        category,
                        Timestamp.from(cutoff)));
    return result == null ? 0L : result;
  }

  /**
   * Substitui o {@code payload} por {@code {"anonymized": true}} para linhas além do cutoff que
   * ainda não foram anonimizadas. Não toca em rows já anonimizadas (idempotência).
   */
  private long anonymize(String category, Instant cutoff) {
    Long result =
        tx.execute(
            s ->
                (long)
                    jdbc.update(
                        "UPDATE audit.audit_event "
                            + "SET payload = ?::jsonb "
                            + "WHERE event_category = ? "
                            + "AND occurred_at < ? "
                            + "AND payload IS NOT NULL "
                            + "AND payload::text <> ?",
                        ANONYMIZED_PAYLOAD,
                        category,
                        Timestamp.from(cutoff),
                        ANONYMIZED_PAYLOAD));
    return result == null ? 0L : result;
  }
}
