package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.BranchQueryPort;
import dev.dimi.erp.cadastros.BranchView;
import dev.dimi.erp.cadastros.internal.domain.Branch;
import dev.dimi.erp.cadastros.internal.domain.BranchFiscal;
import dev.dimi.erp.cadastros.internal.persistence.BranchFiscalRepository;
import dev.dimi.erp.cadastros.internal.persistence.BranchRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapter interno que expõe a porta {@link BranchQueryPort}. Resolve {@link Branch} + {@link
 * BranchFiscal} (1:1 opcional) numa única projeção pública {@link BranchView}.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class BranchQueryService implements BranchQueryPort {

  private final BranchRepository branches;
  private final BranchFiscalRepository branchFiscals;

  @Override
  public Optional<BranchView> findById(BranchId id) {
    return branches.findById(id.value()).map(this::toView);
  }

  @Override
  public boolean exists(BranchId id) {
    return branches.existsById(id.value());
  }

  @Override
  public Optional<BranchView> findMain() {
    return branches.findByMainTrue().map(this::toView);
  }

  @Override
  public List<BranchView> listActive() {
    return branches.findByActiveTrue().stream().map(this::toView).toList();
  }

  @Override
  public boolean canEmitInvoice(BranchId id) {
    return branches
        .findById(id.value())
        .filter(Branch::isActive)
        .flatMap(b -> branchFiscals.findById(b.id().value()))
        .isPresent();
  }

  private BranchView toView(Branch b) {
    BranchView.FiscalView fiscal =
        branchFiscals.findById(b.id().value()).map(BranchQueryService::toFiscalView).orElse(null);
    return new BranchView(b.id(), b.getCode(), b.getName(), b.isMain(), b.isActive(), fiscal);
  }

  private static BranchView.FiscalView toFiscalView(BranchFiscal f) {
    BranchView.CertificateInfo cert =
        f.hasCertificate()
            ? new BranchView.CertificateInfo(
                f.getCertThumbprint(),
                f.getCertValidFrom(),
                f.getCertValidUntil(),
                f.getCertSubjectCnpj())
            : null;
    return new BranchView.FiscalView(
        f.getCnpj(),
        f.getIe(),
        f.getIm(),
        f.isIeExempt(),
        f.getTaxRegime(),
        f.getCsosnDefault(),
        f.getCstIcmsDefault(),
        f.getCnae(),
        f.getNfeEnvironment(),
        cert);
  }
}
