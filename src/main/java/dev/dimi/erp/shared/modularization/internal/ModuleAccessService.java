package dev.dimi.erp.shared.modularization.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.dimi.erp.shared.modularization.CoreModuleImmutableException;
import dev.dimi.erp.shared.modularization.ModuleCatalog;
import dev.dimi.erp.shared.modularization.ModuleNotFoundException;
import dev.dimi.erp.shared.modularization.ModuleNotInstalledException;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Serviço central de gating de módulos por tenant.
 *
 * <p>Consultado por {@link ModuleAccessFilter} (path-based, no filter chain HTTP) e pelo {@code
 * ModuleAccessGuard} (code-based, em cross-module flows). Mantém cache Caffeine in-memory por chave
 * {@code (tenantKey, moduleCode)} com TTL curto (30s) para que toggles via {@code PATCH
 * /api/v1/tenants/me/modules/{code}} reflitam rapidamente no gating sem precisar de invalidação
 * cross-node — invalidação local cobre o nó que recebeu o toggle; outros nós convergem dentro do
 * TTL.
 *
 * <p>Default sem row em {@code tenant_module}: módulo considerado {@code enabled=true} (compat com
 * tenants pré-slice 11.5 e com módulos opcionais que vêm enabled de fábrica).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ModuleAccessService {

  private final TenantModuleRepository repo;

  /** Cache local TTL=30s; chave {@code "tenantKey|moduleCode"}. */
  private final Cache<String, Boolean> cache =
      Caffeine.newBuilder().maximumSize(1024).expireAfterWrite(Duration.ofSeconds(30)).build();

  /**
   * Retorna {@code true} se o módulo está enabled no tenant atual. Core modules ({@link
   * ModuleCatalog.ModuleDef#isCore()}) são <b>sempre</b> habilitados. Códigos fora do catálogo
   * passam ({@code true}) — não é responsabilidade deste service decidir sobre paths que não
   * pertencem a nenhum módulo conhecido.
   */
  public boolean isEnabled(String tenantKey, String moduleCode) {
    return ModuleCatalog.findByCode(moduleCode)
        .map(def -> def.isCore() || lookup(tenantKey, moduleCode))
        .orElse(true);
  }

  /**
   * Variante path-based usada pelo {@link ModuleAccessFilter}. Retorna o {@code ModuleDef}
   * desativado que cobre o {@code requestPath} dado, ou {@link Optional#empty()} quando o path não
   * bate em módulo conhecido ou o módulo está habilitado.
   */
  public Optional<ModuleCatalog.ModuleDef> blockedModuleForPath(
      String tenantKey, String requestPath) {
    return ModuleCatalog.resolveByPath(requestPath)
        .filter(def -> !def.isCore())
        .filter(def -> !lookup(tenantKey, def.code()));
  }

  private boolean lookup(String tenantKey, String moduleCode) {
    String cacheKey = tenantKey + "|" + moduleCode;
    Boolean cached = cache.getIfPresent(cacheKey);
    if (cached != null) {
      return cached;
    }
    boolean enabled = repo.findByCode(moduleCode).map(TenantModule::isEnabled).orElse(true);
    cache.put(cacheKey, enabled);
    return enabled;
  }

  /** Invalida o cache local para {@code (tenantKey, moduleCode)}. */
  public void invalidate(String tenantKey, String moduleCode) {
    cache.invalidate(tenantKey + "|" + moduleCode);
  }

  public List<TenantModule> listEnabled() {
    return repo.findByEnabledTrue();
  }

  public List<TenantModule> listAll() {
    return repo.findAll();
  }

  /**
   * Seed completo do catálogo no tenant ativo. Idempotente: rows já existentes são ignoradas. Para
   * cada {@link ModuleCatalog.ModuleDef} ausente, insere com {@code enabled = installed} — módulos
   * core e fiscal v1 ficam ligados; roadmap futuro v1.1+ ({@code installed=false}) fica desligado
   * para a UI renderizar como "Instalar disponível em breve".
   *
   * <p>Deve rodar dentro de transação ativa no schema do tenant alvo — Hibernate resolve o schema
   * via {@code CurrentTenantResolver}. Chamado por {@code TenantProvisioner} ao criar tenant novo e
   * por {@code TenantModuleBackfillRunner} ao subir o app para tenants pré-slice 11.5.
   *
   * @return número de rows efetivamente inseridas
   */
  @Transactional
  public int seedCatalog() {
    java.util.Set<String> existing = new java.util.HashSet<>();
    for (TenantModule m : repo.findAll()) {
      existing.add(m.getCode());
    }
    int added = 0;
    for (ModuleCatalog.ModuleDef def : ModuleCatalog.all()) {
      if (existing.contains(def.code())) {
        continue;
      }
      repo.save(TenantModule.provisioned(def.code(), def.installed()));
      added++;
    }
    return added;
  }

  /**
   * Alterna o estado de um módulo opcional para o tenant atual. Falha se o módulo é core (imutável)
   * ou não está {@code installed} no catálogo.
   *
   * @param moduleCode código do módulo no {@link ModuleCatalog}
   * @param enable {@code true} para ativar, {@code false} para desativar
   * @param actor UUID do usuário executando a operação (auditoria)
   * @return entidade {@link TenantModule} após persistência
   */
  @Transactional
  public TenantModule toggle(String moduleCode, boolean enable, UUID actor) {
    ModuleCatalog.ModuleDef def =
        ModuleCatalog.findByCode(moduleCode)
            .orElseThrow(() -> new ModuleNotFoundException(moduleCode));
    if (def.isCore()) {
      throw new CoreModuleImmutableException(moduleCode);
    }
    if (!def.installed()) {
      throw new ModuleNotInstalledException(moduleCode);
    }

    TenantModule m =
        repo.findByCode(moduleCode).orElseGet(() -> TenantModule.provisioned(moduleCode, false));
    if (enable) {
      m.enable(actor);
    } else {
      m.disable(actor);
    }
    TenantModule saved = repo.save(m);

    String tenantKey = TenantContext.currentOrThrow().slug();
    invalidate(tenantKey, moduleCode);
    log.info(
        "module toggle tenant={} module={} enabled={} actor={}",
        tenantKey,
        moduleCode,
        enable,
        actor);

    return saved;
  }
}
