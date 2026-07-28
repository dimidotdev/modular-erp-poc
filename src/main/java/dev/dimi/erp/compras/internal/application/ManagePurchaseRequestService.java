package dev.dimi.erp.compras.internal.application;

import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.compras.PurchaseRequestApprovedEvent;
import dev.dimi.erp.compras.PurchaseRequestId;
import dev.dimi.erp.compras.PurchaseRequestRejectedEvent;
import dev.dimi.erp.compras.PurchaseRequestStatus;
import dev.dimi.erp.compras.PurchaseRequestSubmittedEvent;
import dev.dimi.erp.compras.internal.domain.PurchaseRequest;
import dev.dimi.erp.compras.internal.domain.PurchaseRequestItem;
import dev.dimi.erp.compras.internal.persistence.PurchaseRequestItemRepository;
import dev.dimi.erp.compras.internal.persistence.PurchaseRequestRepository;
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
 * Operações sobre {@code purchase_request} (SC) — CRUD + transições de status.
 *
 * <p>v1 (slice 6.2): alçada por valor (RN-COM-003) não enforced — fica para slice futuro quando
 * houver tabela {@code role_purchase_limit}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ManagePurchaseRequestService {

  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String TABLE = "purchase_request";

  private final PurchaseRequestRepository requests;
  private final PurchaseRequestItemRepository items;
  private final PurchaseNumberSequencer sequencer;
  private final ApplicationEventPublisher events;

  @Transactional
  public PurchaseRequestSummary register(RegisterPurchaseRequestCommand cmd) {
    if (cmd.items() == null || cmd.items().isEmpty()) {
      throw new InvalidPurchaseOperationException(
          "empty-request", "SC sem itens; informe pelo menos um item.");
    }
    PurchaseRequest saved = persistWithRetry(cmd);

    List<PurchaseRequestItem> savedItems = new ArrayList<>();
    int line = 1;
    for (RegisterPurchaseRequestCommand.Item raw : cmd.items()) {
      savedItems.add(
          items.save(
              PurchaseRequestItem.create(
                  saved.id().value(),
                  line++,
                  raw.packagingId() == null ? null : raw.packagingId().value(),
                  raw.description(),
                  raw.quantity(),
                  raw.estimatedUnitCost(),
                  raw.notes())));
    }

    log.info(
        "purchase-request created id={} branch={} number={} actor={}",
        saved.id().value(),
        cmd.branchId().value(),
        saved.getNumber(),
        cmd.actor().value());
    return PurchaseRequestSummary.of(
        saved, savedItems.stream().map(PurchaseRequestItemSummary::of).toList());
  }

  @Transactional
  public PurchaseRequestSummary submit(
      PurchaseRequestId requestId, dev.dimi.erp.identidade.UserId actor) {
    PurchaseRequest r = load(requestId);
    if (r.getStatus() != PurchaseRequestStatus.DRAFT) {
      throw new InvalidPurchaseStateException("PurchaseRequest", r.getStatus().name(), "submit");
    }
    r.submit();
    events.publishEvent(
        new PurchaseRequestSubmittedEvent(
            r.id(), currentTenantId(), r.branchIdTyped(), r.requestedByTyped(), Instant.now()));
    log.info("purchase-request submitted id={} actor={}", r.id().value(), actor.value());
    return summary(r);
  }

  @Transactional
  public PurchaseRequestSummary approve(
      PurchaseRequestId requestId, dev.dimi.erp.identidade.UserId actor) {
    PurchaseRequest r = load(requestId);
    if (r.getStatus() != PurchaseRequestStatus.PENDING_APPROVAL
        && r.getStatus() != PurchaseRequestStatus.DRAFT) {
      throw new InvalidPurchaseStateException("PurchaseRequest", r.getStatus().name(), "approve");
    }
    r.approve(actor.value());
    events.publishEvent(
        new PurchaseRequestApprovedEvent(r.id(), currentTenantId(), actor, Instant.now()));
    log.info("purchase-request approved id={} actor={}", r.id().value(), actor.value());
    return summary(r);
  }

  @Transactional
  public PurchaseRequestSummary reject(
      PurchaseRequestId requestId, String reason, dev.dimi.erp.identidade.UserId actor) {
    PurchaseRequest r = load(requestId);
    if (r.getStatus() != PurchaseRequestStatus.PENDING_APPROVAL) {
      throw new InvalidPurchaseStateException("PurchaseRequest", r.getStatus().name(), "reject");
    }
    if (reason == null || reason.isBlank()) {
      throw new InvalidPurchaseOperationException(
          "rejection-reason-required", "Motivo da rejeição é obrigatório.");
    }
    r.reject(actor.value(), reason);
    events.publishEvent(
        new PurchaseRequestRejectedEvent(r.id(), currentTenantId(), reason, actor, Instant.now()));
    return summary(r);
  }

  @Transactional
  public PurchaseRequestSummary cancel(PurchaseRequestId requestId) {
    PurchaseRequest r = load(requestId);
    if (r.getStatus() == PurchaseRequestStatus.ORDERED) {
      throw new InvalidPurchaseStateException("PurchaseRequest", r.getStatus().name(), "cancel");
    }
    r.cancel();
    return summary(r);
  }

  @Transactional(readOnly = true)
  public PurchaseRequestSummary findById(PurchaseRequestId requestId) {
    return summary(load(requestId));
  }

  @Transactional(readOnly = true)
  public Page<PurchaseRequestSummary> listByBranchAndStatus(
      BranchId branchId, PurchaseRequestStatus status, Pageable pageable) {
    return requests.findByBranchIdAndStatus(branchId.value(), status, pageable).map(this::summary);
  }

  private PurchaseRequest load(PurchaseRequestId requestId) {
    return requests
        .findById(requestId.value())
        .orElseThrow(() -> new PurchaseRequestNotFoundException(requestId));
  }

  private PurchaseRequestSummary summary(PurchaseRequest r) {
    List<PurchaseRequestItemSummary> snap =
        items.findByRequestIdOrderByLineNumberAsc(r.id().value()).stream()
            .map(PurchaseRequestItemSummary::of)
            .toList();
    return PurchaseRequestSummary.of(r, snap);
  }

  private PurchaseRequest persistWithRetry(RegisterPurchaseRequestCommand cmd) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(TABLE, cmd.branchId().value());
        PurchaseRequest r =
            PurchaseRequest.draft(
                number,
                cmd.branchId().value(),
                cmd.actor().value(),
                cmd.departmentId(),
                cmd.costCenterId(),
                cmd.neededBy(),
                cmd.priority(),
                cmd.justification());
        return requests.saveAndFlush(r);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.debug("purchase-request number collision, retrying attempt={}", attempts);
      }
    }
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
