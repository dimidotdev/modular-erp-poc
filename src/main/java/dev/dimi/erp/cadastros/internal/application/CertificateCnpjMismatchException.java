package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.shared.exception.DomainException;
import java.util.Map;
import org.springframework.http.HttpStatus;

/**
 * CNPJ extraído do CN do PKCS#12 não bate com o CNPJ registrado em {@code branch_fiscal} — emitir
 * NFe assinada por esse certificado seria rejeitado pela SEFAZ. Recusamos antes de armazenar no Key
 * Vault.
 */
public class CertificateCnpjMismatchException extends DomainException {

  private final BranchId branchId;
  private final String certificateCnpj;
  private final String branchCnpj;

  public CertificateCnpjMismatchException(
      BranchId branchId, String certificateCnpj, String branchCnpj) {
    super(
        "cadastros.certificate-cnpj-mismatch",
        HttpStatus.UNPROCESSABLE_ENTITY,
        "CNPJ do certificado diverge da filial",
        "CNPJ no CN do certificado ("
            + certificateCnpj
            + ") não confere com o CNPJ da filial ("
            + branchCnpj
            + ").");
    this.branchId = branchId;
    this.certificateCnpj = certificateCnpj;
    this.branchCnpj = branchCnpj;
  }

  @Override
  public Map<String, Object> properties() {
    return Map.of(
        "branchId", branchId.value().toString(),
        "certificateCnpj", certificateCnpj,
        "branchCnpj", branchCnpj);
  }
}
