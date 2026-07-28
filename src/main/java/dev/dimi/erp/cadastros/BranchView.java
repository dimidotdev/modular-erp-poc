package dev.dimi.erp.cadastros;

import java.time.Instant;

/**
 * Projeção pública de {@code Branch} para consumo cross-module via {@link BranchQueryPort}.
 *
 * <p>{@link FiscalView} é embutido (não nullable na semântica, mas o port pode devolver vazio) com
 * dados necessários para emissão fiscal — quando ausente, {@link BranchQueryPort#canEmitInvoice}
 * retorna {@code false} (RN-CAD-006).
 *
 * @param id identificador tipado
 * @param code código operacional (ex: {@code 001}, {@code MTZ})
 * @param name descrição humana
 * @param main {@code true} se é a filial matriz (única por tenant — RN-CAD-014)
 * @param active se está ativa
 * @param fiscal dados fiscais quando configurados; {@code null} se a filial ainda não pode emitir
 */
public record BranchView(
    BranchId id, String code, String name, boolean main, boolean active, FiscalView fiscal) {

  /**
   * Subset de {@code branch_fiscal} suficiente para o módulo fiscal montar a NFe.
   *
   * @param cnpj CNPJ próprio da filial (14 dígitos)
   * @param ie inscrição estadual; pode ser {@code null} se isento
   * @param im inscrição municipal; pode ser {@code null}
   * @param ieExempt {@code true} se a filial é isenta de IE
   * @param taxRegime regime tributário (SIMPLES, PRESUMIDO, REAL, MEI)
   * @param csosnDefault CSOSN padrão para Simples (ex: {@code 102})
   * @param cstIcmsDefault CST ICMS padrão para Lucro Real/Presumido (ex: {@code 00})
   * @param cnae CNAE principal
   * @param nfeEnvironment {@code PRODUCAO} ou {@code HOMOLOGACAO}
   * @param certificate metadados do certificado A1 instalado; {@code null} se não houver
   */
  public record FiscalView(
      String cnpj,
      String ie,
      String im,
      boolean ieExempt,
      TaxRegime taxRegime,
      String csosnDefault,
      String cstIcmsDefault,
      String cnae,
      String nfeEnvironment,
      CertificateInfo certificate) {}

  /**
   * Metadados do certificado A1 instalado. O material criptográfico (PFX + senha) vive no Key Vault
   * — consumidores que precisam assinar devem usar o service interno do módulo cadastros.
   *
   * @param thumbprint SHA-1 hex do certificado (uppercase, 40 chars)
   * @param validFrom início da validade
   * @param validUntil fim da validade (usado por {@code fiscal} para alertar expiração)
   * @param subjectCnpj CNPJ extraído do CN do certificado
   */
  public record CertificateInfo(
      String thumbprint, Instant validFrom, Instant validUntil, String subjectCnpj) {}
}
