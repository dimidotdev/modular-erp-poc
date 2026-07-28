package dev.dimi.erp.auditoria.internal.application;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Manutenção da janela de partições mensais de {@code audit.audit_event}. A V4 cria as partições do
 * mês corrente + 2 meses à frente; este service mantém a janela rolante: cria partições futuras
 * (idempotente) e drop de partições antigas além da retenção.
 *
 * <p>Trabalha em DDL com {@link JdbcTemplate} (não JPA) — schema {@code audit} é cross-tenant, fora
 * do {@code search_path} multi-tenant, e {@code CREATE TABLE PARTITION OF} não tem equivalente
 * portátil em JPA. Cada operação é sua própria transação implícita (auto-commit no JdbcTemplate)
 * para não estender locks por toda a manutenção.
 *
 * <p>Nomenclatura das partições (RN-AUD-006, espelha V4): {@code audit.audit_event_YYYY_MM}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PartitionMaintenanceService {

  private static final DateTimeFormatter PART_SUFFIX = DateTimeFormatter.ofPattern("yyyy_MM");

  private final JdbcTemplate jdbc;

  /**
   * Garante que existam partições para os próximos {@code monthsAhead} meses (além do mês
   * corrente). Idempotente — usa {@code CREATE TABLE IF NOT EXISTS PARTITION OF}.
   *
   * @param monthsAhead quantos meses à frente do mês corrente garantir (típico: 2)
   * @return quantidade de partições efetivamente criadas neste run
   */
  public int ensurePartitionsAhead(int monthsAhead) {
    if (monthsAhead < 0) {
      throw new IllegalArgumentException("monthsAhead must be >= 0");
    }
    YearMonth current = YearMonth.now();
    int created = 0;
    for (int i = 0; i <= monthsAhead; i++) {
      YearMonth target = current.plusMonths(i);
      if (createPartitionIfMissing(target)) {
        created++;
      }
    }
    log.info(
        "PartitionMaintenance ensurePartitionsAhead monthsAhead={} created={}",
        monthsAhead,
        created);
    return created;
  }

  /**
   * Drop de partições mensais cujo {@code upper bound} (início do mês seguinte) seja {@code <=
   * cutoffDate}. Em outras palavras: drop apenas das partições inteiramente anteriores ao corte.
   * Usa o catálogo {@code pg_inherits + pg_class} para listar partições reais (resistente a renames
   * manuais).
   *
   * @param cutoffDate data de corte — partições com fim {@code <= cutoffDate} são dropadas
   * @return quantidade de partições efetivamente dropadas
   */
  public int dropPartitionsBefore(LocalDate cutoffDate) {
    if (cutoffDate == null) {
      throw new IllegalArgumentException("cutoffDate obrigatório");
    }
    List<String> partitions = listPartitions();
    int dropped = 0;
    for (String partName : partitions) {
      YearMonth ym = parseYearMonth(partName);
      if (ym == null) {
        log.warn(
            "PartitionMaintenance partição {} fora do padrão audit_event_YYYY_MM — ignorando",
            partName);
        continue;
      }
      // upper bound exclusivo da partição = primeiro dia do mês seguinte
      LocalDate upperBound = ym.plusMonths(1).atDay(1);
      if (!upperBound.isAfter(cutoffDate)) {
        try {
          jdbc.execute("DROP TABLE audit." + quoteIdent(partName));
          dropped++;
          log.info(
              "PartitionMaintenance dropped partition={} upperBound={} cutoff={}",
              partName,
              upperBound,
              cutoffDate);
        } catch (RuntimeException e) {
          log.error("PartitionMaintenance falhou dropando partição {} — seguindo", partName, e);
        }
      }
    }
    return dropped;
  }

  /**
   * Cria a partição do mês {@code target} se ainda não existir. Retorna {@code true} quando a
   * tabela foi efetivamente criada — usa {@code CREATE TABLE IF NOT EXISTS}, então o retorno é
   * baseado em pré-check via catálogo (caso contrário não teria como diferenciar criação x noop).
   */
  private boolean createPartitionIfMissing(YearMonth target) {
    String partName = "audit_event_" + target.format(PART_SUFFIX);
    if (partitionExists(partName)) {
      return false;
    }
    LocalDate start = target.atDay(1);
    LocalDate end = target.plusMonths(1).atDay(1);
    String ddl =
        Objects.requireNonNull(
            String.format(
                "CREATE TABLE IF NOT EXISTS audit.%s PARTITION OF audit.audit_event "
                    + "FOR VALUES FROM ('%s') TO ('%s')",
                quoteIdent(partName), start, end));
    jdbc.execute(ddl);
    log.info("PartitionMaintenance created partition={} range=[{},{})", partName, start, end);
    return true;
  }

  private boolean partitionExists(String partName) {
    Long count =
        jdbc.queryForObject(
            "SELECT count(*) FROM pg_class c "
                + "JOIN pg_namespace n ON n.oid = c.relnamespace "
                + "WHERE n.nspname = 'audit' AND c.relname = ?",
            Long.class,
            partName);
    return count != null && count > 0;
  }

  /** Lista nomes de partições filhas de {@code audit.audit_event} via {@code pg_inherits}. */
  private List<String> listPartitions() {
    return jdbc.queryForList(
        "SELECT child.relname "
            + "FROM pg_inherits i "
            + "JOIN pg_class parent ON parent.oid = i.inhparent "
            + "JOIN pg_class child  ON child.oid = i.inhrelid "
            + "JOIN pg_namespace pn ON pn.oid = parent.relnamespace "
            + "JOIN pg_namespace cn ON cn.oid = child.relnamespace "
            + "WHERE pn.nspname = 'audit' AND parent.relname = 'audit_event' "
            + "AND cn.nspname = 'audit'",
        String.class);
  }

  /** Extrai {@code YearMonth} do sufixo {@code _YYYY_MM} ou {@code null} se fora do padrão. */
  private static YearMonth parseYearMonth(String partName) {
    int idx = partName.lastIndexOf("audit_event_");
    if (idx < 0) return null;
    String suffix = partName.substring(idx + "audit_event_".length());
    try {
      return YearMonth.parse(suffix, PART_SUFFIX);
    } catch (RuntimeException e) {
      return null;
    }
  }

  /**
   * Aspas duplas dobradas para escapar identificadores Postgres. Os nomes aqui são gerados pelo
   * próprio service ({@code audit_event_YYYY_MM}) — defesa em profundidade.
   */
  private static String quoteIdent(String ident) {
    return "\"" + ident.replace("\"", "\"\"") + "\"";
  }
}
