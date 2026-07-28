package dev.dimi.erp.contratos;

/**
 * Índice de reajuste aplicável a contratos.
 *
 * <ul>
 *   <li>{@code IGPM}: Índice Geral de Preços do Mercado (FGV).
 *   <li>{@code IPCA}: Índice Nacional de Preços ao Consumidor Amplo (IBGE).
 *   <li>{@code INPC}: Índice Nacional de Preços ao Consumidor (IBGE).
 *   <li>{@code MANUAL}: percentual livre informado pelo operador (sem vínculo a índice oficial).
 * </ul>
 *
 * Valores casam com o CHECK constraint da migration V23 ({@code contract_adjustment.index}).
 */
public enum AdjustmentIndex {
  IGPM,
  IPCA,
  INPC,
  MANUAL
}
