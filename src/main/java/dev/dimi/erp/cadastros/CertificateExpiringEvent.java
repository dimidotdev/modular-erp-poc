package dev.dimi.erp.cadastros;

import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Job diário detectou que um certificado A1 está próximo de expirar (RN-CAD-009). Disparado uma vez
 * por marco (30, 15, 7 dias). Consumido por {@code fiscal} (avisos visuais) e por um eventual
 * listener de notificação que envia email para o OWNER do tenant.
 *
 * @param branchId filial cuja fiscal carrega o certificado
 * @param tenantId tenant
 * @param thumbprint identifica a versão exata do PKCS#12 que está expirando
 * @param validUntil data/hora exata de expiração
 * @param daysRemaining dias inteiros até {@code validUntil} (30, 15 ou 7)
 * @param occurredAt timestamp do disparo
 */
public record CertificateExpiringEvent(
    BranchId branchId,
    TenantId tenantId,
    String thumbprint,
    Instant validUntil,
    int daysRemaining,
    Instant occurredAt) {}
