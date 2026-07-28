package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.TaxRegime;
import dev.dimi.erp.cadastros.internal.domain.Branch;
import dev.dimi.erp.cadastros.internal.domain.BranchFiscal;
import java.time.Instant;
import java.util.UUID;

/**
 * Projeção plana de {@link Branch} (+ {@link BranchFiscal} opcional) para resposta de service.
 * Quando a filial ainda não tem dados fiscais configurados o sub-record {@link FiscalSummary} fica
 * {@code null} — sinaliza para o cliente que ela não pode emitir notas (RN-CAD-006).
 */
public record BranchSummary(
    BranchId id,
    String code,
    String name,
    boolean main,
    boolean active,
    UUID addressId,
    FiscalSummary fiscal,
    Instant createdAt,
    Instant updatedAt) {

  public static BranchSummary of(Branch b, BranchFiscal fiscal) {
    return new BranchSummary(
        b.id(),
        b.getCode(),
        b.getName(),
        b.isMain(),
        b.isActive(),
        b.getAddressId(),
        fiscal == null ? null : FiscalSummary.of(fiscal),
        b.getCreatedAt(),
        b.getUpdatedAt());
  }

  /**
   * Sub-record com dados fiscais não-sensíveis. Material criptográfico do certificado (PFX, senha)
   * fica no Key Vault — esta projeção carrega só os metadados necessários para UI mostrar
   * status/validade.
   */
  public record FiscalSummary(
      String cnpj,
      String ie,
      String im,
      boolean ieExempt,
      TaxRegime taxRegime,
      String csosnDefault,
      String cstIcmsDefault,
      String cnae,
      String nfeEnvironment,
      Instant updatedAt,
      CertificateSummary certificate) {

    public static FiscalSummary of(BranchFiscal f) {
      return new FiscalSummary(
          f.getCnpj(),
          f.getIe(),
          f.getIm(),
          f.isIeExempt(),
          f.getTaxRegime(),
          f.getCsosnDefault(),
          f.getCstIcmsDefault(),
          f.getCnae(),
          f.getNfeEnvironment(),
          f.getUpdatedAt(),
          f.hasCertificate() ? CertificateSummary.of(f) : null);
    }
  }

  /** Metadados do certificado A1 instalado. Não expõe nome do secret no Key Vault. */
  public record CertificateSummary(
      String thumbprint, Instant validFrom, Instant validUntil, String subjectCnpj) {

    public static CertificateSummary of(BranchFiscal f) {
      return new CertificateSummary(
          f.getCertThumbprint(),
          f.getCertValidFrom(),
          f.getCertValidUntil(),
          f.getCertSubjectCnpj());
    }
  }
}
