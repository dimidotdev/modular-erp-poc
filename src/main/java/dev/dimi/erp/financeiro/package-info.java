/**
 * Módulo Financeiro.
 *
 * <p>Bounded context conforme docs/domain/. Subpacote {@code internal/} contém a implementação
 * privada; outros módulos só podem importar tipos da raiz deste pacote (API pública: eventos, IDs
 * tipados, ports/services expostos).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Financeiro",
    allowedDependencies = {
      "identidade",
      "cadastros",
      "movimentacao",
      "compras",
      "ordemservico",
      "contratos",
      "fiscal",
      "shared"
    })
package dev.dimi.erp.financeiro;
