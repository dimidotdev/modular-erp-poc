package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseRequestStatus;
import dev.dimi.erp.compras.QuotationClosedEvent;
import dev.dimi.erp.compras.QuotationId;
import dev.dimi.erp.compras.QuotationStatus;
import dev.dimi.erp.compras.QuotationSupplierStatus;
import dev.dimi.erp.compras.internal.domain.PurchaseRequest;
import dev.dimi.erp.compras.internal.domain.Quotation;
import dev.dimi.erp.compras.internal.domain.QuotationResponse;
import dev.dimi.erp.compras.internal.domain.QuotationSupplier;
import dev.dimi.erp.compras.internal.domain.QuotationSupplierKey;
import dev.dimi.erp.compras.internal.persistence.PurchaseRequestRepository;
import dev.dimi.erp.compras.internal.persistence.QuotationRepository;
import dev.dimi.erp.compras.internal.persistence.QuotationResponseRepository;
import dev.dimi.erp.compras.internal.persistence.QuotationSupplierRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações sobre {@code quotation}: abertura, convite a fornecedores, registro de respostas e
 * fechamento.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class QuotationService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "quotation";

  private final QuotationRepository quotations;
  private final QuotationSupplierRepository quotationSuppliers;
  private final QuotationResponseRepository quotationResponses;
  private final PurchaseRequestRepository purchaseRequests;
  private final PurchaseNumberSequencer sequencer;
  private final ApplicationEventPublisher events;

  @Transactional
  public QuotationSummary open(OpenQuotationCommand cmd) {
    PurchaseRequest request = null;
    if (cmd.requestId() != null) {
      request =
          purchaseRequests
              .findById(cmd.requestId().value())
              .orElseThrow(() -> new PurchaseRequestNotFoundException(cmd.requestId()));
      if (request.getStatus() != PurchaseRequestStatus.APPROVED
          && request.getStatus() != PurchaseRequestStatus.QUOTING) {
        throw new InvalidPurchaseStateException(
            "PurchaseRequest", request.getStatus().name(), "open-quotation");
      }
      request.markQuoting();
    }
    Quotation saved = persistWithRetry(cmd);
    log.info(
        "quotation opened id={} branch={} number={} request={} actor={}",
        saved.id().value(),
        cmd.branchId().value(),
        saved.getNumber(),
        cmd.requestId() == null ? null : cmd.requestId().value(),
        cmd.actor().value());
    return summary(saved);
  }

  @Transactional
  public QuotationSummary invite(InviteSupplierCommand cmd) {
    Quotation q = load(cmd.quotationId());
    if (q.getStatus() != QuotationStatus.OPEN && q.getStatus() != QuotationStatus.SENT) {
      throw new InvalidPurchaseStateException("Quotation", q.getStatus().name(), "invite");
    }
    QuotationSupplierKey key = new QuotationSupplierKey(q.id().value(), cmd.supplierId().value());
    if (quotationSuppliers.findById(key).isPresent()) {
      throw new InvalidPurchaseOperationException(
          "supplier-already-invited",
          "Fornecedor já convidado para esta cotação: " + cmd.supplierId().value());
    }
    QuotationSupplier invited =
        QuotationSupplier.invite(
            q.id().value(),
            cmd.supplierId().value(),
            cmd.paymentConditionId(),
            cmd.deliveryDays(),
            cmd.freightValue(),
            cmd.notes());
    quotationSuppliers.save(invited);
    if (q.getStatus() == QuotationStatus.OPEN) {
      q.markSent();
    }
    log.info(
        "quotation supplier invited quotation={} supplier={} actor={}",
        q.id().value(),
        cmd.supplierId().value(),
        cmd.actor().value());
    return summary(q);
  }

  @Transactional
  public QuotationSummary registerResponse(RegisterQuotationResponseCommand cmd) {
    Quotation q = load(cmd.quotationId());
    if (q.getStatus() != QuotationStatus.SENT && q.getStatus() != QuotationStatus.RECEIVING) {
      throw new InvalidPurchaseStateException(
          "Quotation", q.getStatus().name(), "register-response");
    }
    QuotationSupplierKey key = new QuotationSupplierKey(q.id().value(), cmd.supplierId().value());
    QuotationSupplier supplier =
        quotationSuppliers
            .findById(key)
            .orElseThrow(
                () ->
                    new InvalidPurchaseOperationException(
                        "supplier-not-invited",
                        "Fornecedor não convidado para esta cotação: " + cmd.supplierId().value()));
    if (cmd.lines() == null || cmd.lines().isEmpty()) {
      throw new InvalidPurchaseOperationException(
          "empty-response", "Resposta sem linhas; informe pelo menos uma linha cotada.");
    }
    quotationResponses.deleteAll(
        quotationResponses.findByQuotationIdAndSupplierId(
            q.id().value(), cmd.supplierId().value()));
    for (RegisterQuotationResponseCommand.Line line : cmd.lines()) {
      quotationResponses.save(
          QuotationResponse.create(
              q.id().value(),
              cmd.supplierId().value(),
              line.requestItemLine(),
              line.packagingId() == null ? null : line.packagingId().value(),
              line.description(),
              line.quantity(),
              line.unitPrice(),
              line.notes()));
    }
    supplier.markResponded();
    if (q.getStatus() == QuotationStatus.SENT) {
      q.markReceiving();
    }
    log.info(
        "quotation response registered quotation={} supplier={} lines={} actor={}",
        q.id().value(),
        cmd.supplierId().value(),
        cmd.lines().size(),
        cmd.actor().value());
    return summary(q);
  }

  @Transactional
  public QuotationSummary close(QuotationId quotationId, dev.dimi.erp.identidade.UserId actor) {
    Quotation q = load(quotationId);
    if (q.getStatus() != QuotationStatus.SENT && q.getStatus() != QuotationStatus.RECEIVING) {
      throw new InvalidPurchaseStateException("Quotation", q.getStatus().name(), "close");
    }
    List<QuotationSupplier> invited = quotationSuppliers.findByKeyQuotationId(q.id().value());
    boolean anyResponded =
        invited.stream().anyMatch(s -> s.getStatus() == QuotationSupplierStatus.RESPONDED);
    if (!anyResponded) {
      throw new InvalidPurchaseOperationException(
          "no-responses", "Não é possível fechar cotação sem nenhuma resposta de fornecedor.");
    }
    invited.stream()
        .filter(s -> s.getStatus() == QuotationSupplierStatus.INVITED)
        .forEach(QuotationSupplier::markExpired);
    q.close();
    events.publishEvent(
        new QuotationClosedEvent(
            q.id(), currentTenantId(), q.branchIdTyped(), actor, Instant.now()));
    log.info("quotation closed id={} actor={}", q.id().value(), actor.value());
    return summary(q);
  }

  @Transactional
  public QuotationSummary cancel(QuotationId quotationId) {
    Quotation q = load(quotationId);
    if (q.getStatus() == QuotationStatus.CLOSED || q.getStatus() == QuotationStatus.CANCELLED) {
      throw new InvalidPurchaseStateException("Quotation", q.getStatus().name(), "cancel");
    }
    q.cancel();
    return summary(q);
  }

  @Transactional(readOnly = true)
  public QuotationSummary findById(QuotationId quotationId) {
    return summary(load(quotationId));
  }

  @Transactional(readOnly = true)
  public Page<QuotationSummary> listByBranchAndStatus(
      BranchId branchId, QuotationStatus status, Pageable pageable) {
    return quotations
        .findByBranchIdAndStatus(branchId.value(), status, pageable)
        .map(this::summary);
  }

  Quotation load(QuotationId quotationId) {
    return quotations
        .findById(quotationId.value())
        .orElseThrow(() -> new QuotationNotFoundException(quotationId));
  }

  QuotationSummary summary(Quotation q) {
    List<QuotationSupplierSummary> suppliers =
        quotationSuppliers.findByKeyQuotationId(q.id().value()).stream()
            .map(QuotationSupplierSummary::of)
            .toList();
    List<QuotationResponseSummary> responses =
        quotationResponses.findByQuotationId(q.id().value()).stream()
            .map(QuotationResponseSummary::of)
            .toList();
    return QuotationSummary.of(q, suppliers, responses);
  }

  private Quotation persistWithRetry(OpenQuotationCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(TABLE, cmd.branchId().value());
        Quotation q =
            Quotation.open(
                number,
                cmd.branchId().value(),
                cmd.requestId() == null ? null : cmd.requestId().value(),
                cmd.deadline());
        return quotations.saveAndFlush(q);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("quotation number collision, retrying attempt={}", attempts);
      }
    }
  }

  /**
   * Helper de pacote — usado pelo {@code PurchaseOrderService} para validar PCs originados em
   * cotação.
   */
  List<QuotationResponse> findResponsesBySupplier(
      QuotationId quotationId, java.util.UUID supplierId) {
    return new ArrayList<>(
        quotationResponses.findByQuotationIdAndSupplierId(quotationId.value(), supplierId));
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
