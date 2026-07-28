package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.CertificateUploadedEvent;
import dev.dimi.erp.cadastros.internal.domain.Branch;
import dev.dimi.erp.cadastros.internal.domain.BranchFiscal;
import dev.dimi.erp.cadastros.internal.persistence.BranchFiscalRepository;
import dev.dimi.erp.cadastros.internal.persistence.BranchRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sobre o certificado A1 de uma filial: instalar/substituir e remover. Material fica em
 * {@link CertificateMaterialStore} (Azure Key Vault em produção, filesystem local em dev/test);
 * metadados ficam em {@link BranchFiscal}. RN-CAD-007 / RN-CAD-008.
 *
 * <p>O CNPJ extraído do certificado é confrontado com {@code branch_fiscal.cnpj} antes do
 * armazenamento — divergência aborta o fluxo sem efeito colateral em Key Vault.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BranchCertificateService {

  private static final String SECRET_NAME_PREFIX = "cert-branch-";

  private final BranchRepository branches;
  private final BranchFiscalRepository branchFiscals;
  private final CertificateMaterialStore certificateStore;
  private final PkcsCertificateExtractor extractor;
  private final ApplicationEventPublisher events;

  @Transactional
  public BranchSummary install(InstallBranchCertificateCommand cmd) {
    Branch b =
        branches.findById(cmd.branchId().value()).orElseThrow(() -> notFound(cmd.branchId()));
    if (!b.isActive()) {
      throw new InvalidBranchStateException(
          b.id(), "inactive", "Filial inativa não pode receber certificado.");
    }
    BranchFiscal fiscal =
        branchFiscals
            .findById(b.id().value())
            .orElseThrow(
                () ->
                    new InvalidBranchStateException(
                        b.id(),
                        "missing-fiscal",
                        "Registre dados fiscais (CNPJ) antes de instalar o certificado."));

    CertificateMetadata meta = extractor.extract(cmd.pfxBytes(), cmd.password());
    if (!meta.subjectCnpj().equals(fiscal.getCnpj())) {
      throw new CertificateCnpjMismatchException(b.id(), meta.subjectCnpj(), fiscal.getCnpj());
    }
    if (meta.validUntil().isBefore(Instant.now())) {
      throw new InvalidBranchStateException(
          b.id(), "expired-certificate", "Certificado já vencido em " + meta.validUntil() + ".");
    }

    String secretName = secretName(b.id());
    String previousThumbprint = fiscal.hasCertificate() ? fiscal.getCertThumbprint() : null;

    certificateStore.put(secretName, new CertificateMaterial(cmd.pfxBytes(), cmd.password()));
    fiscal.installCertificate(
        secretName, meta.thumbprint(), meta.validFrom(), meta.validUntil(), meta.subjectCnpj());

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new CertificateUploadedEvent(
            b.id(),
            new TenantId(tenant.id()),
            meta.thumbprint(),
            meta.validFrom(),
            meta.validUntil(),
            meta.subjectCnpj(),
            previousThumbprint,
            cmd.actor(),
            Instant.now()));

    log.info(
        "certificado instalado branchId={} thumbprint={} validUntil={} replaced={}",
        b.id().value(),
        meta.thumbprint(),
        meta.validUntil(),
        previousThumbprint);
    return BranchSummary.of(b, fiscal);
  }

  @Transactional
  public BranchSummary clear(BranchId branchId, UserId actor) {
    Branch b = branches.findById(branchId.value()).orElseThrow(() -> notFound(branchId));
    BranchFiscal fiscal =
        branchFiscals
            .findById(b.id().value())
            .orElseThrow(
                () ->
                    new InvalidBranchStateException(
                        b.id(), "missing-fiscal", "Filial não possui dados fiscais."));
    if (!fiscal.hasCertificate()) {
      throw new InvalidBranchStateException(
          b.id(), "missing-certificate", "Filial não possui certificado instalado.");
    }

    String secretName = fiscal.getCertKvSecretName();
    String thumbprint = fiscal.getCertThumbprint();

    certificateStore.purge(secretName);
    fiscal.clearCertificate();

    log.info(
        "certificado removido branchId={} thumbprint={} actor={}",
        b.id().value(),
        thumbprint,
        actor == null ? "system" : actor.value());
    return BranchSummary.of(b, fiscal);
  }

  static String secretName(BranchId branchId) {
    return SECRET_NAME_PREFIX + branchId.value();
  }

  private static BranchNotFoundException notFound(BranchId id) {
    return new BranchNotFoundException(id);
  }
}
