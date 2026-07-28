package dev.dimi.erp.cadastros.internal.application;

import dev.dimi.erp.cadastros.BranchCreatedEvent;
import dev.dimi.erp.cadastros.BranchDeactivatedEvent;
import dev.dimi.erp.cadastros.BranchFiscalUpdatedEvent;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.BranchReactivatedEvent;
import dev.dimi.erp.cadastros.BranchUpdatedEvent;
import dev.dimi.erp.cadastros.internal.domain.Branch;
import dev.dimi.erp.cadastros.internal.domain.BranchFiscal;
import dev.dimi.erp.cadastros.internal.persistence.BranchFiscalRepository;
import dev.dimi.erp.cadastros.internal.persistence.BranchRepository;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.shared.documents.Cnpj;
import dev.dimi.erp.shared.multitenancy.Tenant;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sobre {@code Branch} (+ {@code BranchFiscal}) dentro do tenant atual.
 *
 * <p>Cobre o CRUD do slice 3.3: registrar, atualizar, desativar/reativar, listar/buscar, upsert de
 * dados fiscais. Certificado A1 fica para slice 3.5 ({@code UploadBranchCertificateService}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BranchService {

  private final BranchRepository branches;
  private final BranchFiscalRepository branchFiscals;
  private final ApplicationEventPublisher events;

  // ---------------------------------------------------------------------------
  // Registro
  // ---------------------------------------------------------------------------

  @Transactional
  public BranchSummary register(RegisterBranchCommand cmd) {
    if (branches.existsByCode(cmd.code())) {
      throw new BranchAlreadyExistsException("code", cmd.code());
    }
    if (cmd.isMain()) {
      branches.clearCurrentMain(null);
    }

    Branch saved =
        branches.save(Branch.create(cmd.code(), cmd.name(), cmd.isMain(), cmd.addressId()));

    Tenant tenant = TenantContext.currentOrThrow();
    TenantId tenantId = new TenantId(tenant.id());
    events.publishEvent(
        new BranchCreatedEvent(
            saved.id(),
            tenantId,
            saved.getCode(),
            saved.getName(),
            saved.isMain(),
            cmd.registeredBy(),
            Instant.now()));

    log.info(
        "filial registrada id={} code={} isMain={}",
        saved.id().value(),
        saved.getCode(),
        saved.isMain());
    return BranchSummary.of(saved, null);
  }

  // ---------------------------------------------------------------------------
  // Atualização
  // ---------------------------------------------------------------------------

  @Transactional
  public BranchSummary update(UpdateBranchCommand cmd) {
    Branch b =
        branches.findById(cmd.branchId().value()).orElseThrow(() -> notFound(cmd.branchId()));
    if (!b.isActive()) {
      throw new InvalidBranchStateException(
          b.id(), "inactive", "Filial inativa não pode ser editada. Reative antes.");
    }

    Set<String> changed = new HashSet<>();
    if (cmd.name() != null && !cmd.name().equals(b.getName())) changed.add("name");
    if (cmd.addressId() != null && !cmd.addressId().equals(b.getAddressId()))
      changed.add("addressId");

    b.update(cmd.name(), cmd.addressId());

    if (cmd.isMain() != null) {
      boolean targetMain = cmd.isMain();
      if (targetMain && !b.isMain()) {
        branches.clearCurrentMain(b.id().value());
        b.markAsMain();
        changed.add("main");
      } else if (!targetMain && b.isMain()) {
        throw new InvalidBranchStateException(
            b.id(),
            "demote-main-not-allowed",
            "Para desmarcar matriz, promova outra filial via PATCH com isMain=true.");
      }
    }

    if (!changed.isEmpty()) {
      Tenant tenant = TenantContext.currentOrThrow();
      events.publishEvent(
          new BranchUpdatedEvent(
              b.id(), new TenantId(tenant.id()), changed, cmd.updatedBy(), Instant.now()));
      log.info("filial atualizada id={} fields={}", b.id().value(), changed);
    }
    return BranchSummary.of(b, branchFiscals.findById(b.id().value()).orElse(null));
  }

  // ---------------------------------------------------------------------------
  // Soft-delete / reativação
  // ---------------------------------------------------------------------------

  @Transactional
  public BranchSummary deactivate(BranchId branchId, String reason, UserId actor) {
    Branch b = branches.findById(branchId.value()).orElseThrow(() -> notFound(branchId));
    if (!b.isActive()) {
      throw new InvalidBranchStateException(b.id(), "already-inactive", "Filial já está inativa.");
    }
    if (b.isMain()) {
      throw new InvalidBranchStateException(
          b.id(),
          "main-cannot-deactivate",
          "Filial matriz não pode ser desativada. Promova outra antes.");
    }
    b.deactivate();

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new BranchDeactivatedEvent(
            b.id(), new TenantId(tenant.id()), reason, actor, Instant.now()));
    log.info("filial desativada id={} reason={}", b.id().value(), reason);
    return BranchSummary.of(b, branchFiscals.findById(b.id().value()).orElse(null));
  }

  @Transactional
  public BranchSummary reactivate(BranchId branchId, UserId actor) {
    Branch b = branches.findById(branchId.value()).orElseThrow(() -> notFound(branchId));
    if (b.isActive()) {
      throw new InvalidBranchStateException(b.id(), "already-active", "Filial já está ativa.");
    }
    b.reactivate();

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new BranchReactivatedEvent(b.id(), new TenantId(tenant.id()), actor, Instant.now()));
    log.info("filial reativada id={}", b.id().value());
    return BranchSummary.of(b, branchFiscals.findById(b.id().value()).orElse(null));
  }

  // ---------------------------------------------------------------------------
  // Dados fiscais (upsert)
  // ---------------------------------------------------------------------------

  /**
   * Cria ou atualiza {@code branch_fiscal}. CNPJ é validado via VO {@link Cnpj} e exigido apenas no
   * primeiro registro; em updates ele é imutável (substituição implicaria mover histórico de NFe).
   */
  @Transactional
  public BranchSummary upsertFiscal(UpsertBranchFiscalCommand cmd) {
    Branch b =
        branches.findById(cmd.branchId().value()).orElseThrow(() -> notFound(cmd.branchId()));
    if (!b.isActive()) {
      throw new InvalidBranchStateException(
          b.id(), "inactive", "Filial inativa não pode ter dados fiscais alterados.");
    }

    Optional<BranchFiscal> existing = branchFiscals.findById(b.id().value());
    BranchFiscal fiscal;
    if (existing.isPresent()) {
      fiscal = existing.get();
      if (cmd.cnpj() != null && !cmd.cnpj().isBlank()) {
        String incoming = Cnpj.of(cmd.cnpj()).value();
        if (!incoming.equals(fiscal.getCnpj())) {
          throw new InvalidBranchStateException(
              b.id(),
              "cnpj-immutable",
              "CNPJ da filial é imutável após registro. Crie uma nova filial.");
        }
      }
      fiscal.update(
          cmd.ie(),
          cmd.im(),
          cmd.ieExempt(),
          cmd.taxRegime(),
          cmd.csosnDefault(),
          cmd.cstIcmsDefault(),
          cmd.cnae(),
          cmd.nfeEnvironment());
    } else {
      if (cmd.cnpj() == null || cmd.cnpj().isBlank()) {
        throw new InvalidBranchStateException(
            b.id(), "missing-cnpj", "CNPJ obrigatório no primeiro registro fiscal.");
      }
      if (cmd.taxRegime() == null) {
        throw new InvalidBranchStateException(
            b.id(), "missing-tax-regime", "Regime tributário obrigatório no registro fiscal.");
      }
      String cnpj = Cnpj.of(cmd.cnpj()).value();
      if (branchFiscals.findByCnpj(cnpj).isPresent()) {
        throw new BranchAlreadyExistsException("cnpj", cnpj);
      }
      fiscal =
          branchFiscals.save(
              BranchFiscal.create(
                  b.id().value(),
                  cnpj,
                  cmd.ie(),
                  cmd.im(),
                  cmd.ieExempt() != null && cmd.ieExempt(),
                  cmd.taxRegime(),
                  cmd.csosnDefault(),
                  cmd.cstIcmsDefault(),
                  cmd.cnae(),
                  cmd.nfeEnvironment()));
    }

    Tenant tenant = TenantContext.currentOrThrow();
    events.publishEvent(
        new BranchFiscalUpdatedEvent(
            b.id(),
            new TenantId(tenant.id()),
            fiscal.getCnpj(),
            fiscal.getTaxRegime(),
            fiscal.getNfeEnvironment(),
            cmd.actor(),
            Instant.now()));
    log.info(
        "dados fiscais filial id={} cnpj={} regime={}",
        b.id().value(),
        fiscal.getCnpj(),
        fiscal.getTaxRegime());
    return BranchSummary.of(b, fiscal);
  }

  // ---------------------------------------------------------------------------
  // Queries
  // ---------------------------------------------------------------------------

  @Transactional(readOnly = true)
  public BranchSummary findById(BranchId branchId) {
    Branch b = branches.findById(branchId.value()).orElseThrow(() -> notFound(branchId));
    return BranchSummary.of(b, branchFiscals.findById(branchId.value()).orElse(null));
  }

  @Transactional(readOnly = true)
  public Page<BranchSummary> list(Pageable pageable) {
    Page<Branch> page = branches.findAll(pageable);
    return page.map(b -> BranchSummary.of(b, branchFiscals.findById(b.id().value()).orElse(null)));
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  private static BranchNotFoundException notFound(BranchId id) {
    return new BranchNotFoundException(id);
  }
}
