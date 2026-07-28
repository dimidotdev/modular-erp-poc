package dev.dimi.erp.cadastros.internal.web.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.TaxRegime;
import dev.dimi.erp.cadastros.internal.application.BranchSummary;
import java.time.Instant;
import java.util.UUID;

/**
 * Resposta HTTP plana para um {@link BranchSummary}. Sub-record {@code fiscal} é omitido quando a
 * filial ainda não tem dados fiscais (sinaliza ao cliente que não pode emitir notas — RN-CAD-006).
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record BranchApiResponse(
    BranchId id,
    String code,
    String name,
    boolean main,
    boolean active,
    UUID addressId,
    FiscalResponse fiscal,
    Instant createdAt,
    Instant updatedAt) {

  public static BranchApiResponse from(BranchSummary s) {
    return new BranchApiResponse(
        s.id(),
        s.code(),
        s.name(),
        s.main(),
        s.active(),
        s.addressId(),
        s.fiscal() == null ? null : FiscalResponse.from(s.fiscal()),
        s.createdAt(),
        s.updatedAt());
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record FiscalResponse(
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
      CertificateResponse certificate) {

    public static FiscalResponse from(BranchSummary.FiscalSummary f) {
      return new FiscalResponse(
          f.cnpj(),
          f.ie(),
          f.im(),
          f.ieExempt(),
          f.taxRegime(),
          f.csosnDefault(),
          f.cstIcmsDefault(),
          f.cnae(),
          f.nfeEnvironment(),
          f.updatedAt(),
          f.certificate() == null ? null : CertificateResponse.from(f.certificate()));
    }
  }

  @JsonInclude(JsonInclude.Include.NON_NULL)
  public record CertificateResponse(
      String thumbprint, Instant validFrom, Instant validUntil, String subjectCnpj) {

    public static CertificateResponse from(BranchSummary.CertificateSummary c) {
      return new CertificateResponse(
          c.thumbprint(), c.validFrom(), c.validUntil(), c.subjectCnpj());
    }
  }
}
