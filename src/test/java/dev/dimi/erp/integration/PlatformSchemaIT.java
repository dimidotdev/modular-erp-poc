package dev.dimi.erp.integration;

import static org.assertj.core.api.Assertions.assertThat;

import dev.dimi.erp.support.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Garante que o boot da aplicação aplica todas as migrations globais ({@code db/migration/global/})
 * e que os schemas {@code shared}, {@code audit} e {@code platform} ficam consistentes.
 */
class PlatformSchemaIT extends AbstractIntegrationTest {

  @Autowired JdbcTemplate jdbc;

  @Test
  void migrations_globais_criam_schemas_e_tabelas_esperadas() {
    // Schemas criados pelo Flyway (create-schemas=true).
    var schemas =
        jdbc.queryForList(
            "SELECT schema_name FROM information_schema.schemata "
                + "WHERE schema_name IN ('shared','audit','platform')",
            String.class);
    assertThat(schemas).containsExactlyInAnyOrder("shared", "audit", "platform");

    // Tabelas em shared.
    var sharedTables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'shared' "
                + "AND table_type = 'BASE TABLE'",
            String.class);
    assertThat(sharedTables).contains("tenant", "permission");

    // Tabela particionada em audit.
    var auditTables =
        jdbc.queryForList(
            "SELECT table_name FROM information_schema.tables WHERE table_schema = 'audit' "
                + "AND table_type IN ('BASE TABLE','PARTITIONED TABLE')",
            String.class);
    assertThat(auditTables).contains("audit_event");
  }

  @Test
  void seed_de_permissions_carrega_catalogo_inicial() {
    Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM shared.permission", Integer.class);
    assertThat(total).isNotNull().isGreaterThanOrEqualTo(80);

    // Spot-check algumas permissions críticas que outros slices vão usar.
    var codes =
        jdbc.queryForList(
            "SELECT code FROM shared.permission WHERE code IN "
                + "('nfe.emitir','venda.cancelar','platform.tenant.criar','pessoa.anonimizar')",
            String.class);
    assertThat(codes)
        .containsExactlyInAnyOrder(
            "nfe.emitir", "venda.cancelar", "platform.tenant.criar", "pessoa.anonimizar");

    // Sensitivity flag.
    Boolean nfeCancelarSensitive =
        jdbc.queryForObject(
            "SELECT is_sensitive FROM shared.permission WHERE code = 'nfe.cancelar'",
            Boolean.class);
    assertThat(nfeCancelarSensitive).isTrue();
  }

  @Test
  void audit_event_tem_particoes_do_mes_corrente_e_dois_proximos() {
    Integer partitions =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM pg_inherits i "
                + "JOIN pg_class p ON p.oid = i.inhparent "
                + "JOIN pg_namespace n ON n.oid = p.relnamespace "
                + "WHERE n.nspname = 'audit' AND p.relname = 'audit_event'",
            Integer.class);
    assertThat(partitions).isNotNull().isGreaterThanOrEqualTo(3);
  }
}
