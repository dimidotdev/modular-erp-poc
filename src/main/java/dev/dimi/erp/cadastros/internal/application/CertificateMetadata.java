package dev.dimi.erp.cadastros.internal.application;

import java.time.Instant;

/**
 * Metadados extraídos de um PKCS#12 A1 — projeção determinística sobre o {@code X509Certificate}
 * que o {@link PkcsCertificateExtractor} produz a partir dos bytes do PFX. Não persistido
 * diretamente: a entidade {@code BranchFiscal} consome estes campos via {@code
 * installCertificate(...)}.
 *
 * @param thumbprint SHA-1 hex uppercase do DER (40 chars) — identifica a versão do certificado
 * @param validFrom {@code notBefore} do X.509
 * @param validUntil {@code notAfter} do X.509 — driver dos alertas de expiração (RN-CAD-009)
 * @param subjectCnpj CNPJ extraído do CN (14 dígitos numéricos); validado pelo service contra o
 *     CNPJ da filial — não casa = recusa upload
 */
public record CertificateMetadata(
    String thumbprint, Instant validFrom, Instant validUntil, String subjectCnpj) {}
