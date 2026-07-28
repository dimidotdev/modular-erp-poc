package dev.dimi.erp.shared.modularization;

/**
 * Port público de provisioning de {@code tenant_module} rows. Chamado por (a) {@code
 * TenantProvisioner} em {@code identidade} ao criar um tenant novo e (b) {@code
 * TenantModuleBackfillRunner} ao subir o app para tenants pré-existentes ao slice 11.5.
 *
 * <p>Seed regra: para cada {@link ModuleCatalog.ModuleDef}, cria row com {@code enabled =
 * installed} — módulos {@code installed=true} (core + fiscal v1) começam ligados; roadmap futuro
 * ({@code installed=false}: frota, patrimônio, RH, POS, agro, imobiliária, obras) começam {@code
 * enabled=false} para a UI renderizar como "Instalar disponível em breve".
 *
 * <p>Idempotente: rows já existentes não são sobrescritas; só códigos ausentes são inseridos.
 *
 * <p>Implementação concreta em {@code internal/adapter/TenantModuleProvisionerAdapter}.
 */
public interface TenantModuleProvisioner {

  /**
   * Seed completo do catálogo no tenant ativo (TenantContext do caller). Retorna número de rows
   * efetivamente inseridas (módulos já existentes são ignorados).
   *
   * <p>Deve ser chamado dentro de uma transação ativa no schema do tenant alvo — Hibernate resolve
   * o schema via {@code CurrentTenantResolver}.
   */
  int seedAll();
}
