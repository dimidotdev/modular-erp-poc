package dev.dimi.erp.contratos;

/**
 * Tipo de item de contrato exposto na API pública (REST + integração entre módulos). Espelha o enum
 * interno {@code dev.dimi.erp.contratos.internal.domain.ContractItem.ItemType} mas vive aqui na
 * raiz pública para poder ser usado por DTOs do controller sem violar encapsulamento do {@code
 * internal/} — mesmo padrão de {@link dev.dimi.erp.ordemservico.OsBillingType}.
 *
 * <p>{@code PRODUCT}: vínculo com {@code packaging} (exige {@code packagingId}, NFe na geração).
 * {@code SERVICE}: vínculo com {@code serviceCode} livre (LC 116, NFSe na geração).
 *
 * <p>O mapping interno⇄público acontece no {@code CreateContractRequest} (toSpec()) e no {@code
 * ContractItemApiResponse#from(...)}.
 */
public enum ItemType {
  PRODUCT,
  SERVICE
}
