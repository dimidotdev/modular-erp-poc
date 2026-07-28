package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Dados fiscais de uma filial foram criados ou atualizados. Consumido por {@code fiscal} (invalida
 * cache) e por {@code auditoria}. Campos de certificado A1 ficam para slice 3.5 e disparam evento
 * próprio {@code CertificateUploadedEvent} quando chegarem.
 */
public record BranchFiscalUpdatedEvent(
    BranchId branchId,
    TenantId tenantId,
    String cnpj,
    TaxRegime taxRegime,
    String nfeEnvironment,
    UserId actor,
    Instant occurredAt) {}
