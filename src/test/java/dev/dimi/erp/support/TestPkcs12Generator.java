package dev.dimi.erp.support;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Date;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

/**
 * Gera PKCS#12 sintéticos para ITs do certificado A1. Forja um par RSA-2048 + cert self-signed cujo
 * CN segue o padrão ICP-Brasil ({@code "<nome>:<cnpj14>"}) — não confunde com cert real porque o
 * Issuer é o próprio Subject (não a AC Raiz Brasileira).
 */
public final class TestPkcs12Generator {

  private static final String SIG_ALG = "SHA256withRSA";

  private TestPkcs12Generator() {}

  public static Pfx generate(
      String displayName, String cnpj14, Instant notBefore, Instant notAfter, String password) {
    try {
      KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
      kpg.initialize(2048);
      KeyPair kp = kpg.generateKeyPair();

      String dn = "CN=" + displayName + ":" + cnpj14;
      org.bouncycastle.asn1.x500.X500Name subject = new org.bouncycastle.asn1.x500.X500Name(dn);
      X509v3CertificateBuilder builder =
          new JcaX509v3CertificateBuilder(
              subject,
              BigInteger.valueOf(System.currentTimeMillis()),
              Date.from(notBefore),
              Date.from(notAfter),
              subject,
              kp.getPublic());
      ContentSigner signer = new JcaContentSignerBuilder(SIG_ALG).build(kp.getPrivate());
      X509Certificate cert =
          new JcaX509CertificateConverter().getCertificate(builder.build(signer));

      KeyStore ks = KeyStore.getInstance("PKCS12");
      ks.load(null, null);
      ks.setKeyEntry(
          "test-alias", kp.getPrivate(), password.toCharArray(), new X509Certificate[] {cert});

      ByteArrayOutputStream out = new ByteArrayOutputStream();
      ks.store(out, password.toCharArray());
      return new Pfx(out.toByteArray(), password, cert);
    } catch (Exception e) {
      throw new IllegalStateException("falha gerando PKCS#12 de teste", e);
    }
  }

  public record Pfx(byte[] bytes, String password, X509Certificate cert) {} // gitleaks:allow
}
