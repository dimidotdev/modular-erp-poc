package dev.dimi.erp.produto.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.produto.BrandId;
import dev.dimi.erp.produto.Classification;

/**
 * Comando de registro de produto. Cria a hierarquia inicial: produto + variant default + (opcional)
 * packaging default, atendendo o critério "produto sem variedade explícita" (RN-PRD-003).
 *
 * @param skuRoot prefixo do SKU (único no tenant)
 * @param name nome de exibição
 * @param description opcional
 * @param brandId opcional
 * @param classification operacional/fiscal
 * @param ncm null permitido apenas para SERVICO (RN-PRD-002)
 * @param cest opcional
 * @param origin null vira "0" no domínio
 * @param defaultVariantCode default "PADRAO" quando null — usado para criar a variant invisível
 * @param defaultPackagingSku quando não-null, cria packaging default associada à variant default
 * @param defaultPackagingBarcode opcional
 * @param actor usuário disparador (audit + evento)
 */
public record RegisterProductCommand(
    String skuRoot,
    String name,
    String description,
    BrandId brandId,
    Classification classification,
    String ncm,
    String cest,
    String origin,
    String defaultVariantCode,
    String defaultPackagingSku,
    String defaultPackagingBarcode,
    UserId actor) {}
