package dev.dimi.erp.cadastros.internal.application;

import java.util.Optional;

/**
 * Port de armazenamento do material do certificado A1 (PFX + senha). Implementação default em
 * dev/test grava em filesystem ({@code LocalFsCertificateMaterialStore}); produção usa Azure Key
 * Vault Secret ({@code KeyVaultCertificateMaterialStore}) — seleção via {@code
 * cadastros.certificate.store} no {@code application.yml}.
 *
 * <p>Cada {@code secretName} representa uma filial (convenção: {@code cert-branch-{uuid}}). Chamar
 * {@link #put} com nome existente cria nova versão (retenção 90 dias por soft-delete do KV — ver
 * RN-CAD-008).
 */
public interface CertificateMaterialStore {

  /**
   * Grava (ou cria nova versão de) o material associado a {@code secretName}. Implementação Key
   * Vault chama {@code setSecret} — versões antigas ficam disponíveis pelo histórico do Secret.
   */
  void put(String secretName, CertificateMaterial material);

  /**
   * Recupera o material da versão corrente. Vazio se o secret não existe (não confundir com "filial
   * sem certificado": isso é responsabilidade da entidade {@code BranchFiscal}).
   */
  Optional<CertificateMaterial> get(String secretName);

  /**
   * Marca o secret para deleção permanente. Em Key Vault entra em soft-delete (retenção 90 dias) —
   * não chamar {@code purgeDeletedSecret} aqui, isso é decisão de operações.
   */
  void purge(String secretName);
}
