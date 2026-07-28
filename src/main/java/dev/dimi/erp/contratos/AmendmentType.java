package dev.dimi.erp.contratos;

/**
 * Tipo de aditivo contratual.
 *
 * <ul>
 *   <li>{@code EXTENSION}: prorroga {@code endDate}.
 *   <li>{@code VALUE}: altera {@code baseValue} (uso negocial, distinto de reajuste por índice).
 *   <li>{@code ITEM_ADD}: inclui item novo no contrato.
 *   <li>{@code ITEM_REMOVE}: remove item existente.
 *   <li>{@code OTHER}: cláusula textual sem efeito estruturado.
 * </ul>
 *
 * Valores casam com o CHECK constraint da migration V23 ({@code contract_amendment.type}).
 */
public enum AmendmentType {
  EXTENSION,
  VALUE,
  ITEM_ADD,
  ITEM_REMOVE,
  OTHER
}
