/**
 * Módulo Fiscal.
 *
 * <p>Bounded context conforme docs/domain/. Subpacote {@code internal/} contém a implementação
 * privada; outros módulos só podem importar tipos da raiz deste pacote (API pública: eventos, IDs
 * tipados, ports/services expostos).
 *
 * <p>A partir do slice 9.2b o fiscal também consome {@code OsQueryPort} (API pública de {@code
 * ordemservico}) para emissão from-OS — NFe sobre os produtos consumidos e NFSe sobre os serviços
 * prestados, em conjunto, na saga de faturamento da OS. Por isso {@code ordemservico} entra em
 * {@code allowedDependencies}.
 */
@org.springframework.modulith.ApplicationModule(
    displayName = "Fiscal",
    allowedDependencies = {
      "identidade",
      "cadastros",
      "produto",
      "movimentacao",
      "ordemservico",
      "shared"
    })
package dev.dimi.erp.fiscal;
