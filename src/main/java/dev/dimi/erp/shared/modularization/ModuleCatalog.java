package dev.dimi.erp.shared.modularization;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Catálogo estático de módulos do ERP. Define metadados (nome, categoria, isCore, pathPrefixes)
 * usado pelo {@code ModuleAccessFilter} para gating de requisições e pelo endpoint {@code GET
 * /api/v1/tenants/me/modules} para o frontend renderizar Configurações &gt; Módulos.
 *
 * <p>Slice 11.5 (v1): core sempre on; {@code fiscal} é o único candidato opcional v1. Módulos v1.1+
 * (frota, patrimônio, RH, POS, agronegócio, imobiliária, obras) são listados aqui já com {@code
 * installed=false} para que o frontend possa mockar tela de "instalar" antes do backend existir.
 */
public final class ModuleCatalog {

  private ModuleCatalog() {}

  public record ModuleDef(
      String code,
      String displayName,
      String category, // "core", "fiscal", "operacional", "rh", "vendas", "vertical"
      boolean isCore,
      boolean installed, // true = backend implementado; false = roadmap futuro
      List<String> pathPrefixes,
      Set<String> dependsOn) {}

  // ===========================================================================
  // CORE (sempre ligados, isCore=true)
  // ===========================================================================
  private static final ModuleDef IDENTIDADE =
      new ModuleDef(
          "identidade",
          "Identidade & Acesso",
          "core",
          true,
          true,
          List.of(
              "/api/v1/auth", "/api/v1/admin/users", "/api/v1/platform/tenants", "/api/v1/tenants"),
          Set.of());
  private static final ModuleDef CADASTROS =
      new ModuleDef(
          "cadastros",
          "Cadastros",
          "core",
          true,
          true,
          List.of("/api/v1/cadastros"),
          Set.of("identidade"));
  private static final ModuleDef PRODUTO =
      new ModuleDef(
          "produto",
          "Produto & Preço",
          "core",
          true,
          true,
          List.of("/api/v1/produtos"),
          Set.of("cadastros"));
  private static final ModuleDef ESTOQUE =
      new ModuleDef(
          "estoque", "Estoque", "core", true, true, List.of("/api/v1/estoque"), Set.of("produto"));
  private static final ModuleDef MOVIMENTACAO =
      new ModuleDef(
          "movimentacao",
          "Movimentação Comercial",
          "core",
          true,
          true,
          List.of("/api/v1/movimentacao"),
          Set.of("produto", "estoque", "cadastros"));
  private static final ModuleDef COMPRAS =
      new ModuleDef(
          "compras",
          "Compras",
          "core",
          true,
          true,
          List.of("/api/v1/compras"),
          Set.of("produto", "estoque", "cadastros"));
  private static final ModuleDef FINANCEIRO =
      new ModuleDef(
          "financeiro",
          "Financeiro",
          "core",
          true,
          true,
          List.of("/api/v1/financeiro"),
          Set.of("cadastros"));
  private static final ModuleDef ORDEMSERVICO =
      new ModuleDef(
          "ordemservico",
          "Ordem de Serviço",
          "core",
          true,
          true,
          List.of("/api/v1/ordens-servico"),
          Set.of("produto", "estoque", "cadastros"));
  private static final ModuleDef CONTRATOS =
      new ModuleDef(
          "contratos",
          "Contratos",
          "core",
          true,
          true,
          List.of("/api/v1/contratos"),
          Set.of("cadastros", "produto", "financeiro"));
  private static final ModuleDef AUDITORIA =
      new ModuleDef(
          "auditoria",
          "Auditoria",
          "core",
          true,
          true,
          List.of("/api/v1/auditoria"),
          Set.of("identidade"));

  // ===========================================================================
  // OPCIONAL v1 (isCore=false, installed=true, default enabled=true)
  // ===========================================================================
  private static final ModuleDef FISCAL =
      new ModuleDef(
          "fiscal",
          "Fiscal (NFe/NFSe)",
          "fiscal",
          false,
          true,
          List.of("/api/v1/fiscal"),
          Set.of("cadastros", "produto", "ordemservico"));

  // ===========================================================================
  // ROADMAP v1.1+ (isCore=false, installed=false; frontend mockable)
  // ===========================================================================
  private static final ModuleDef FROTA =
      new ModuleDef("frota", "Frota & Veículos", "vertical", false, false, List.of(), Set.of());
  private static final ModuleDef PATRIMONIO =
      new ModuleDef("patrimonio", "Patrimônio", "vertical", false, false, List.of(), Set.of());
  private static final ModuleDef RH =
      new ModuleDef("rh", "Recursos Humanos", "vertical", false, false, List.of(), Set.of());
  private static final ModuleDef POS =
      new ModuleDef("pos", "POS / PDV", "vendas", false, false, List.of(), Set.of());
  private static final ModuleDef AGRO =
      new ModuleDef("agronegocio", "Agronegócio", "vertical", false, false, List.of(), Set.of());
  private static final ModuleDef IMOBILIARIA =
      new ModuleDef("imobiliaria", "Imobiliária", "vertical", false, false, List.of(), Set.of());
  private static final ModuleDef OBRAS =
      new ModuleDef("obras", "Obras & Projetos", "vertical", false, false, List.of(), Set.of());

  private static final List<ModuleDef> ALL =
      List.of(
          IDENTIDADE,
          CADASTROS,
          PRODUTO,
          ESTOQUE,
          MOVIMENTACAO,
          COMPRAS,
          FINANCEIRO,
          ORDEMSERVICO,
          CONTRATOS,
          AUDITORIA,
          FISCAL,
          FROTA,
          PATRIMONIO,
          RH,
          POS,
          AGRO,
          IMOBILIARIA,
          OBRAS);

  private static final Map<String, ModuleDef> BY_CODE =
      ALL.stream().collect(java.util.stream.Collectors.toUnmodifiableMap(ModuleDef::code, m -> m));

  public static List<ModuleDef> all() {
    return ALL;
  }

  public static java.util.Optional<ModuleDef> findByCode(String code) {
    return java.util.Optional.ofNullable(BY_CODE.get(code));
  }

  public static List<String> allModuleCodes() {
    return ALL.stream().map(ModuleDef::code).toList();
  }

  public static List<String> coreModuleCodes() {
    return ALL.stream().filter(ModuleDef::isCore).map(ModuleDef::code).toList();
  }

  public static List<String> installedToggleableCodes() {
    return ALL.stream().filter(m -> m.installed() && !m.isCore()).map(ModuleDef::code).toList();
  }

  /** Resolve module code de uma URL path. Returns empty se path não bate em nenhum prefix. */
  public static java.util.Optional<ModuleDef> resolveByPath(String requestPath) {
    for (ModuleDef m : ALL) {
      for (String prefix : m.pathPrefixes()) {
        if (requestPath.startsWith(prefix)) return java.util.Optional.of(m);
      }
    }
    return java.util.Optional.empty();
  }
}
