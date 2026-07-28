package dev.dimi.erp.cadastros.internal.adapter;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.dimi.erp.cadastros.internal.application.CertificateMaterial;
import dev.dimi.erp.cadastros.internal.application.CertificateMaterialStore;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.Base64;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Adapter dev/test que grava o material do certificado em arquivos JSON locais — um por filial,
 * nomeados {@code {secretName}.json}. NÃO usar em produção: sem encryption-at-rest, sem
 * versionamento, sem audit log de acesso. Selecionado quando {@code
 * erp-poc.cadastros.certificate.store} está ausente ou em {@code localfs}.
 *
 * <p>O diretório é criado no primeiro {@code put}. Permissões do filesystem ditam a proteção (em
 * Linux, {@code chmod 700} no diretório pai já basta para ambientes locais).
 */
@Component
@ConditionalOnProperty(
    name = "erp-poc.cadastros.certificate.store",
    havingValue = "localfs",
    matchIfMissing = true)
@Slf4j
class LocalFsCertificateMaterialStore implements CertificateMaterialStore {

  private static final Set<PosixFilePermission> DIRECTORY_PERMISSIONS =
      EnumSet.of(
          PosixFilePermission.OWNER_READ,
          PosixFilePermission.OWNER_WRITE,
          PosixFilePermission.OWNER_EXECUTE);
  private static final Set<PosixFilePermission> FILE_PERMISSIONS =
      EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

  private final Path baseDir;
  private final ObjectMapper json;

  LocalFsCertificateMaterialStore(
      @Value("${erp-poc.cadastros.certificate.localfs.dir:./var/cert-local}") String baseDir,
      ObjectMapper json,
      Environment environment) {
    boolean exclusivelyLocalOrTest =
        environment.getActiveProfiles().length > 0
            && Arrays.stream(environment.getActiveProfiles())
                .allMatch(profile -> profile.equals("local") || profile.equals("test"));
    if (!exclusivelyLocalOrTest) {
      throw new IllegalStateException(
          "certificate.store=localfs só é permitido nos profiles local/test");
    }
    this.baseDir = Path.of(baseDir).toAbsolutePath().normalize();
    this.json = json;
    log.info("certificate store: localfs dir={}", this.baseDir);
  }

  @Override
  public void put(String secretName, CertificateMaterial material) {
    try {
      Files.createDirectories(baseDir);
      applyPermissions(baseDir, DIRECTORY_PERMISSIONS);
      Path file = resolve(secretName);
      if (Files.isSymbolicLink(file)) {
        throw new IOException("arquivo de certificado não pode ser link simbólico");
      }
      Payload payload =
          new Payload(Base64.getEncoder().encodeToString(material.pfx()), material.password());
      Files.writeString(file, json.writeValueAsString(payload));
      applyPermissions(file, FILE_PERMISSIONS);
      log.debug("certificate localfs put name={} bytes={}", secretName, material.pfx().length);
    } catch (IOException e) {
      throw new UncheckedIOException("falha gravando certificado localfs " + secretName, e);
    }
  }

  @Override
  public Optional<CertificateMaterial> get(String secretName) {
    Path file = resolve(secretName);
    if (!Files.exists(file)) {
      return Optional.empty();
    }
    if (Files.isSymbolicLink(file)) {
      throw new IllegalArgumentException("arquivo de certificado não pode ser link simbólico");
    }
    try {
      Payload p = json.readValue(Files.readAllBytes(file), Payload.class);
      return Optional.of(
          new CertificateMaterial(Base64.getDecoder().decode(p.pfxB64()), p.password()));
    } catch (IOException e) {
      throw new UncheckedIOException("falha lendo certificado localfs " + secretName, e);
    }
  }

  @Override
  public void purge(String secretName) {
    try {
      boolean removed = Files.deleteIfExists(resolve(secretName));
      log.debug("certificate localfs purge name={} removed={}", secretName, removed);
    } catch (IOException e) {
      throw new UncheckedIOException("falha removendo certificado localfs " + secretName, e);
    }
  }

  private Path resolve(String secretName) {
    if (secretName == null || !secretName.matches("^[A-Za-z0-9_-]{1,128}$")) {
      throw new IllegalArgumentException("secretName inválido");
    }
    return baseDir.resolve(secretName + ".json");
  }

  private void applyPermissions(Path path, Set<PosixFilePermission> permissions) {
    try {
      Files.setPosixFilePermissions(path, permissions);
    } catch (UnsupportedOperationException e) {
      log.warn("filesystem sem suporte a permissões POSIX para {}", path);
    } catch (IOException e) {
      throw new UncheckedIOException("falha restringindo permissões de " + path, e);
    }
  }

  private record Payload(
      @JsonProperty("pfx_b64") String pfxB64, @JsonProperty("password") String password) {}
}
