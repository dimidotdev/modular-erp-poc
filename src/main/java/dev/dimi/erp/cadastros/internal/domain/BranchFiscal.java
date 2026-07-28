package dev.dimi.erp.cadastros.internal.domain;

import dev.dimi.erp.cadastros.TaxRegime;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Dados fiscais 1:1 de uma {@link Branch}. Separado para isolar campos sensíveis (referência ao
 * certificado A1 no Key Vault, metadados do PKCS#12) e simplificar permissionamento.
 *
 * <p>O conteúdo do certificado (PFX + senha) vive como Secret no Azure Key Vault — aqui guardamos
 * apenas {@code certKvSecretName} + metadados extraídos no momento do upload. Slice 3.5.
 */
@Entity
@Table(name = "branch_fiscal")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"branchId", "cnpj", "taxRegime"})
public class BranchFiscal {

  @Id
  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "cnpj", nullable = false, length = 14)
  private String cnpj;

  @Column(name = "ie")
  private String ie;

  @Column(name = "im")
  private String im;

  @Column(name = "ie_exempt", nullable = false)
  private boolean ieExempt;

  @Enumerated(EnumType.STRING)
  @Column(name = "tax_regime", nullable = false)
  private TaxRegime taxRegime;

  @Column(name = "csosn_default", length = 3)
  private String csosnDefault;

  @Column(name = "cst_icms_default", length = 2)
  private String cstIcmsDefault;

  @Column(name = "cnae")
  private String cnae;

  @Column(name = "cert_kv_secret_name")
  private String certKvSecretName;

  @Column(name = "cert_thumbprint")
  private String certThumbprint;

  @Column(name = "cert_valid_from")
  private Instant certValidFrom;

  @Column(name = "cert_valid_until")
  private Instant certValidUntil;

  @Column(name = "cert_subject_cnpj", length = 14)
  private String certSubjectCnpj;

  @Column(name = "nfce_csc_id")
  private String nfceCscId;

  @Column(name = "nfce_csc_token_enc")
  private byte[] nfceCscTokenEnc;

  @Column(name = "nfe_environment", nullable = false)
  private String nfeEnvironment;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  // ----------------------------------------------------------------------------
  // Comportamento (slice 3.3)
  // ----------------------------------------------------------------------------

  /** Ambientes válidos para emissão (RN-CAD-007 da NFe). */
  public static final String ENV_PRODUCAO = "PRODUCAO";

  public static final String ENV_HOMOLOGACAO = "HOMOLOGACAO";

  /**
   * Cria os dados fiscais de uma filial sem certificado. Campos de certificado ficam para o slice
   * 3.5 (integração Azure Key Vault). CNPJ já validado pelo caller.
   */
  public static BranchFiscal create(
      UUID branchId,
      String cnpj,
      String ie,
      String im,
      boolean ieExempt,
      dev.dimi.erp.cadastros.TaxRegime taxRegime,
      String csosnDefault,
      String cstIcmsDefault,
      String cnae,
      String nfeEnvironment) {
    BranchFiscal bf = new BranchFiscal();
    bf.branchId = Objects.requireNonNull(branchId, "branchId");
    bf.cnpj = Objects.requireNonNull(cnpj, "cnpj");
    bf.ie = ie;
    bf.im = im;
    bf.ieExempt = ieExempt;
    bf.taxRegime = Objects.requireNonNull(taxRegime, "taxRegime");
    bf.csosnDefault = csosnDefault;
    bf.cstIcmsDefault = cstIcmsDefault;
    bf.cnae = cnae;
    bf.nfeEnvironment = nfeEnvironment != null ? nfeEnvironment : ENV_PRODUCAO;
    bf.updatedAt = Instant.now();
    return bf;
  }

  /**
   * Atualiza campos não-certificado. CNPJ é imutável após registro (alteração demandaria nova
   * filial). Para certificado A1 use métodos específicos do slice 3.5.
   */
  public void update(
      String ie,
      String im,
      Boolean ieExempt,
      dev.dimi.erp.cadastros.TaxRegime taxRegime,
      String csosnDefault,
      String cstIcmsDefault,
      String cnae,
      String nfeEnvironment) {
    if (ie != null) this.ie = ie;
    if (im != null) this.im = im;
    if (ieExempt != null) this.ieExempt = ieExempt;
    if (taxRegime != null) this.taxRegime = taxRegime;
    if (csosnDefault != null) this.csosnDefault = csosnDefault;
    if (cstIcmsDefault != null) this.cstIcmsDefault = cstIcmsDefault;
    if (cnae != null) this.cnae = cnae;
    if (nfeEnvironment != null) this.nfeEnvironment = nfeEnvironment;
  }

  // ----------------------------------------------------------------------------
  // Certificado A1 — slice 3.5
  // ----------------------------------------------------------------------------

  /** Indica se um certificado A1 está instalado (não verifica validade nem se expirou). */
  public boolean hasCertificate() {
    return certKvSecretName != null;
  }

  /**
   * Vincula um certificado A1 já armazenado no Key Vault (operação executada pelo service após o
   * Secret ter sido criado). O CNPJ extraído do certificado deve casar com {@link #cnpj} — caller
   * valida.
   */
  public void installCertificate(
      String kvSecretName,
      String thumbprint,
      Instant validFrom,
      Instant validUntil,
      String subjectCnpj) {
    this.certKvSecretName = Objects.requireNonNull(kvSecretName, "kvSecretName");
    this.certThumbprint = Objects.requireNonNull(thumbprint, "thumbprint");
    this.certValidFrom = Objects.requireNonNull(validFrom, "validFrom");
    this.certValidUntil = Objects.requireNonNull(validUntil, "validUntil");
    this.certSubjectCnpj = Objects.requireNonNull(subjectCnpj, "subjectCnpj");
  }

  /** Limpa a referência local ao certificado. Caller é responsável por purgar o Secret no KV. */
  public void clearCertificate() {
    this.certKvSecretName = null;
    this.certThumbprint = null;
    this.certValidFrom = null;
    this.certValidUntil = null;
    this.certSubjectCnpj = null;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof BranchFiscal other)) return false;
    return branchId != null && branchId.equals(other.branchId);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(branchId);
  }
}
