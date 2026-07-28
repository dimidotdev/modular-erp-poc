package dev.dimi.erp.cadastros.internal.adapter;

import com.azure.core.exception.ResourceNotFoundException;
import com.azure.security.keyvault.secrets.SecretClient;
import com.azure.security.keyvault.secrets.models.KeyVaultSecret;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dimi.erp.cadastros.internal.application.CertificateMaterial;
import dev.dimi.erp.cadastros.internal.application.CertificateMaterialStore;
import java.util.Base64;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adapter de produção: grava {@code {pfx_b64, password}} serializado como JSON num Secret do Azure
 * Key Vault. Habilita encryption-at-rest nativa, soft-delete 90 dias (RN-CAD-008) e versionamento —
 * instalar um novo certificado na mesma filial cria nova versão do mesmo Secret.
 *
 * <p>Selecionado quando {@code erp-poc.cadastros.certificate.store=keyvault}. O bean {@link
 * SecretClient} é provido por {@link KeyVaultClientConfig} (mesma condition).
 */
@Component
@ConditionalOnProperty(name = "erp-poc.cadastros.certificate.store", havingValue = "keyvault")
@Slf4j
class KeyVaultCertificateMaterialStore implements CertificateMaterialStore {

  private final SecretClient secrets;
  private final ObjectMapper json;

  KeyVaultCertificateMaterialStore(SecretClient secrets, ObjectMapper json) {
    this.secrets = secrets;
    this.json = json;
    log.info("certificate store: keyvault endpoint={}", secrets.getVaultUrl());
  }

  @Override
  public void put(String secretName, CertificateMaterial material) {
    try {
      Payload payload =
          new Payload(Base64.getEncoder().encodeToString(material.pfx()), material.password());
      String value = json.writeValueAsString(payload);
      KeyVaultSecret saved = secrets.setSecret(secretName, value);
      log.info(
          "certificate keyvault put name={} version={} bytes={}",
          secretName,
          saved.getProperties().getVersion(),
          material.pfx().length);
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("falha serializando certificado para Key Vault", e);
    }
  }

  @Override
  public Optional<CertificateMaterial> get(String secretName) {
    try {
      KeyVaultSecret secret = secrets.getSecret(secretName);
      Payload p = json.readValue(secret.getValue(), Payload.class);
      return Optional.of(
          new CertificateMaterial(Base64.getDecoder().decode(p.pfxB64()), p.password()));
    } catch (ResourceNotFoundException e) {
      return Optional.empty();
    } catch (JsonProcessingException e) {
      throw new IllegalStateException(
          "falha desserializando certificado do Key Vault " + secretName, e);
    }
  }

  @Override
  public void purge(String secretName) {
    secrets.beginDeleteSecret(secretName);
    log.info("certificate keyvault soft-delete name={}", secretName);
  }

  private record Payload(
      @JsonProperty("pfx_b64") String pfxB64, @JsonProperty("password") String password) {}
}
