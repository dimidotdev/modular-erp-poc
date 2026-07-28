package dev.dimi.erp.produto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Nova embalagem (SKU final) cadastrada num produto/variant. Eventos com {@code PackagingId} são o
 * gatilho para módulos a jusante (estoque, fiscal) materializarem suas próprias projeções.
 *
 * @param packagingId id da embalagem
 * @param variantId variant pai (interno ao módulo; aqui só transita)
 * @param productId produto raiz da hierarquia
 * @param tenantId tenant da operação
 * @param sku SKU informado (único globalmente no tenant)
 * @param barcode opcional, código de barras (EAN/GTIN)
 * @param createdBy usuário que disparou o cadastro
 * @param occurredAt timestamp do registro
 */
public record PackagingCreatedEvent(
    PackagingId packagingId,
    java.util.UUID variantId,
    ProductId productId,
    TenantId tenantId,
    String sku,
    String barcode,
    UserId createdBy,
    Instant occurredAt) {}
