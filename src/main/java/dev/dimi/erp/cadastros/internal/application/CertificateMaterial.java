package dev.dimi.erp.cadastros.internal.application;

import java.util.Arrays;
import java.util.Objects;

/**
 * Material criptográfico de um certificado A1 — bytes do PKCS#12 + senha em claro. Nunca persistir
 * aqui: passa pelo {@link CertificateMaterialStore} para chegar ao Azure Key Vault (encryption-at-
 * rest nativo, HSM-backed). É um record por conveniência; igualdade não é semanticamente útil.
 */
public record CertificateMaterial(byte[] pfx, String password) {

  public CertificateMaterial {
    Objects.requireNonNull(pfx, "pfx");
    Objects.requireNonNull(password, "password");
    if (pfx.length == 0) {
      throw new IllegalArgumentException("pfx vazio");
    }
    if (password.isEmpty()) {
      throw new IllegalArgumentException("password vazio");
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof CertificateMaterial other)) return false;
    return Arrays.equals(pfx, other.pfx) && password.equals(other.password);
  }

  @Override
  public int hashCode() {
    return 31 * Arrays.hashCode(pfx) + password.hashCode();
  }

  @Override
  public String toString() {
    return "CertificateMaterial[pfx=" + pfx.length + "B, password=***]";
  }
}
