package dev.dimi.erp.shared.modularization;

/**
 * Port público para serviços de outros módulos abortarem cedo quando o módulo correspondente está
 * desligado no tenant atual.
 *
 * <p>Use em <b>entrypoints sensíveis</b> que façam side-effects (emissão fiscal, gravação de
 * billing, integração externa). O filter ({@code ModuleAccessFilter} interno) já bloqueia por path
 * HTTP, mas serviços chamados internamente (por eventos, jobs, ou cross-module sync) não passam
 * pelo filter — daí a guarda explícita.
 *
 * <p>Implementação concreta em {@code internal/adapter/ModuleAccessGuardAdapter}; outros módulos
 * injetam por esta interface e o Spring resolve.
 */
public interface ModuleAccessGuard {

  /**
   * Lança {@link ModuleAccessDeniedException} se o módulo {@code moduleCode} está desligado para o
   * tenant atual. Core modules nunca disparam.
   */
  void requireEnabled(String moduleCode);
}
