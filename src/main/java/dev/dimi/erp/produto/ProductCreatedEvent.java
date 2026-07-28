package dev.dimi.erp.produto;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Novo produto cadastrado no tenant via {@code POST /api/v1/produtos}.
 *
 * <p>Consumido por {@code auditoria} (categoria {@code PRODUTO}, tipo {@code PRODUCT_CREATED}).
 *
 * @param productId id do produto
 * @param tenantId tenant onde o produto foi criado
 * @param skuRoot prefixo do SKU informado pelo tenant
 * @param name nome de exibição
 * @param classification operacional/fiscal
 * @param createdBy usuário que disparou o cadastro
 * @param occurredAt timestamp do registro
 */
public record ProductCreatedEvent(
    ProductId productId,
    TenantId tenantId,
    String skuRoot,
    String name,
    Classification classification,
    UserId createdBy,
    Instant occurredAt) {}
