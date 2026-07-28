/**
 * Módulo Shared.
 *
 * <p>Tipo OPEN — código de infraestrutura transversal (Value Objects, exceções, validators)
 * reutilizado por todos os bounded contexts. Ver doc canônica em
 * docs/architecture/04-padroes-codigo.md.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Shared",
    type = org.springframework.modulith.ApplicationModule.Type.OPEN)
package dev.dimi.erp.shared;
