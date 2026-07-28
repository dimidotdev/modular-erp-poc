package dev.dimi.erp.cadastros;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;

/**
 * Certificado A1 instalado (ou substituído) em uma filial. Consumido por {@code fiscal} (invalida
 * cache do material assinante) e por {@code auditoria}. O conteúdo do PFX nunca trafega no evento —
 * apenas metadados não-sensíveis.
 *
 * @param branchId filial alvo
 * @param tenantId tenant
 * @param thumbprint SHA-1 hex do certificado (uppercase, 40 chars) — identifica versão única
 * @param validFrom início da validade do PKCS#12
 * @param validUntil fim da validade — usado por listeners para alertas
 * @param subjectCnpj CNPJ do CN do certificado (confirma que casa com o CNPJ da filial)
 * @param replacedThumbprint thumbprint do certificado anterior, se houve substituição; {@code null}
 *     em primeira instalação
 * @param actor usuário que executou o upload
 * @param occurredAt timestamp do evento
 */
public record CertificateUploadedEvent(
    BranchId branchId,
    TenantId tenantId,
    String thumbprint,
    Instant validFrom,
    Instant validUntil,
    String subjectCnpj,
    String replacedThumbprint,
    UserId actor,
    Instant occurredAt) {}
