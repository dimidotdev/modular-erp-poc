package dev.dimi.erp.cadastros.internal.application;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.Enumeration;
import java.util.HexFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Extrai metadados de um PKCS#12 A1 brasileiro: thumbprint SHA-1, validade e CNPJ do CN do Subject.
 * Não toca em {@link CertificateMaterialStore} — só decodifica os bytes recebidos.
 *
 * <p>O caller (BranchCertificateService) valida que o CNPJ do certificado casa com o CNPJ da filial
 * e só então delega o material ao store + persiste metadados em {@code BranchFiscal}.
 */
@Component
public class PkcsCertificateExtractor {

  /**
   * ICP-Brasil A1 e-CNPJ: CN segue padrão {@code "NOME EMPRESA:CNPJ14"}, sendo o CNPJ os 14 últimos
   * dígitos após o último ":" (DOC-ICP-04 da ITI). Capturamos qualquer sequência de 14 dígitos
   * contígua no CN para tolerar variações (sem hífens/pontos esperados aqui).
   */
  private static final Pattern CN_CNPJ = Pattern.compile("\\b(\\d{14})\\b");

  private static final Pattern CN_FIELD = Pattern.compile("CN=([^,]+)");

  public CertificateMetadata extract(byte[] pfx, String password) {
    X509Certificate cert = loadFirstCertificate(pfx, password);
    String thumbprint = thumbprint(cert);
    String cnpj = extractCnpj(cert);
    return new CertificateMetadata(
        thumbprint, cert.getNotBefore().toInstant(), cert.getNotAfter().toInstant(), cnpj);
  }

  private X509Certificate loadFirstCertificate(byte[] pfx, String password) {
    KeyStore ks;
    try {
      ks = KeyStore.getInstance("PKCS12");
    } catch (java.security.KeyStoreException e) {
      throw new IllegalStateException("provider PKCS12 ausente — JVM corrompida", e);
    }

    try (ByteArrayInputStream in = new ByteArrayInputStream(pfx)) {
      ks.load(in, password.toCharArray());
    } catch (IOException e) {
      if (e.getCause() instanceof UnrecoverableKeyException) {
        throw new InvalidPkcs12PasswordException();
      }
      throw new InvalidPkcs12FileException("PKCS#12 ilegível: " + e.getMessage(), e);
    } catch (java.security.GeneralSecurityException e) {
      throw new InvalidPkcs12FileException("PKCS#12 inválido: " + e.getMessage(), e);
    }

    try {
      Enumeration<String> aliases = ks.aliases();
      while (aliases.hasMoreElements()) {
        String alias = aliases.nextElement();
        Certificate c = ks.getCertificate(alias);
        if (c instanceof X509Certificate x509) {
          return x509;
        }
      }
    } catch (java.security.KeyStoreException e) {
      throw new InvalidPkcs12FileException("falha lendo aliases do PKCS#12", e);
    }
    throw new InvalidPkcs12FileException("nenhum certificado X.509 encontrado no PKCS#12");
  }

  private String thumbprint(X509Certificate cert) {
    try {
      MessageDigest md = MessageDigest.getInstance("SHA-1");
      byte[] digest = md.digest(cert.getEncoded());
      return HexFormat.of().withUpperCase().formatHex(digest);
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-1 indisponível — JVM corrompida", e);
    } catch (CertificateEncodingException e) {
      throw new InvalidPkcs12FileException("falha codificando certificado para thumbprint", e);
    }
  }

  /**
   * Extrai 14 dígitos contíguos do CN do Subject. Lança {@link InvalidPkcs12FileException} se não
   * achar — não é A1 e-CNPJ brasileiro, então o upload é rejeitado antes de tocar o store.
   */
  private String extractCnpj(X509Certificate cert) {
    String subject = cert.getSubjectX500Principal().getName();
    Matcher cn = CN_FIELD.matcher(subject);
    if (cn.find()) {
      Matcher m = CN_CNPJ.matcher(cn.group(1));
      if (m.find()) {
        return m.group(1);
      }
    }
    throw new InvalidPkcs12FileException(
        "CNPJ não encontrado no CN do certificado — não é um A1 e-CNPJ válido");
  }
}
