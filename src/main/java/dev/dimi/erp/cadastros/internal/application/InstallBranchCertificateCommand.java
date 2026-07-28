package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.identidade.UserId;

/**
 * Instala (ou substitui) o certificado A1 de uma filial. Os bytes do PFX e a senha trafegam por
 * este command até o {@link BranchCertificateService}, que valida + extrai metadados + delega ao
 * {@link CertificateMaterialStore}.
 *
 * @param branchId filial alvo
 * @param pfxBytes conteúdo bruto do arquivo {@code .pfx} / {@code .p12}
 * @param password senha do PKCS#12 — exigida para abrir o keystore
 * @param actor usuário que está fazendo o upload (vai para audit log e evento)
 */
public record InstallBranchCertificateCommand(
    BranchId branchId, byte[] pfxBytes, String password, UserId actor) {}
