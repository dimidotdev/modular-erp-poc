/**
 * Módulo Identidade e Acesso.
 *
 * <p>Bounded context conforme docs/domain/. Subpacote {@code internal/} contém a implementação
 * privada; outros módulos só podem importar tipos da raiz deste pacote (API pública: eventos, IDs
 * tipados, ports/services expostos).
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Identidade e Acesso",
    allowedDependencies = {"cadastros", "shared"})
package dev.dimi.erp.identidade;
