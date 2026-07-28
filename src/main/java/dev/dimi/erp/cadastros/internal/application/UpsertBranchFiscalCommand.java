package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.TaxRegime;
import dev.dimi.erp.identidade.UserId;

/**
 * Cria ou atualiza dados fiscais (1:1) de uma filial. CNPJ é exigido no primeiro insert e imutável
 * em updates subsequentes (substituição implicaria mover histórico de NFe para outra entidade).
 *
 * <p>Campos de certificado A1 ficam de fora aqui e ganham endpoint dedicado no slice 3.5.
 */
public record UpsertBranchFiscalCommand(
    BranchId branchId,
    String cnpj,
    String ie,
    String im,
    Boolean ieExempt,
    TaxRegime taxRegime,
    String csosnDefault,
    String cstIcmsDefault,
    String cnae,
    String nfeEnvironment,
    UserId actor) {}
