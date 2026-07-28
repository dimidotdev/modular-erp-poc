package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.InvalidWorkflowTransitionException;
import dev.dimi.erp.ordemservico.OsCancelledEvent;
import dev.dimi.erp.ordemservico.OsClosedEvent;
import dev.dimi.erp.ordemservico.OsStatusChangedEvent;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.ServiceOrderNotFoundException;
import dev.dimi.erp.ordemservico.WorkflowStatusNotFoundException;
import dev.dimi.erp.ordemservico.internal.domain.ServiceOrder;
import dev.dimi.erp.ordemservico.internal.domain.StatusHistory;
import dev.dimi.erp.ordemservico.internal.domain.WorkflowStatus;
import dev.dimi.erp.ordemservico.internal.persistence.ServiceOrderRepository;
import dev.dimi.erp.ordemservico.internal.persistence.StatusHistoryRepository;
import dev.dimi.erp.ordemservico.internal.persistence.WorkflowStatusRepository;
import dev.dimi.erp.ordemservico.internal.persistence.WorkflowTransitionRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Aplica transição de status numa OS. Valida o grafo {@code os_workflow_transition} e propaga
 * eventos de domínio. Cancel ({@code → CANCELLED}) e Close ({@code → CLOSED}) também publicam seus
 * eventos específicos para consumers diferenciarem causa de mudança.
 *
 * <p>Slice 9.1 não dispara saga de estorno em CANCELLED — só muda status. Saga real entra em 9.2.
 */
@Service
public class ChangeStatusService {

  private static final Logger log = LoggerFactory.getLogger(ChangeStatusService.class);

  private final ServiceOrderRepository orders;
  private final WorkflowStatusRepository statuses;
  private final WorkflowTransitionRepository transitions;
  private final StatusHistoryRepository history;
  private final ApplicationEventPublisher events;

  public ChangeStatusService(
      ServiceOrderRepository orders,
      WorkflowStatusRepository statuses,
      WorkflowTransitionRepository transitions,
      StatusHistoryRepository history,
      ApplicationEventPublisher events) {
    this.orders = orders;
    this.statuses = statuses;
    this.transitions = transitions;
    this.history = history;
    this.events = events;
  }

  /**
   * Transição genérica para um status alvo. Use overrides {@link #cancel(ServiceOrderId, String,
   * UserId)} e {@link #close(ServiceOrderId, UserId)} para os casos especiais.
   */
  @Transactional
  public void transitionTo(ServiceOrderId id, String targetCode, String notes, UserId changedBy) {
    transitionInternal(id, targetCode, notes, null, changedBy);
  }

  @Transactional
  public void cancel(ServiceOrderId id, String reason, UserId changedBy) {
    transitionInternal(id, "CANCELLED", null, reason, changedBy);
  }

  @Transactional
  public void close(ServiceOrderId id, UserId changedBy) {
    transitionInternal(id, "CLOSED", null, null, changedBy);
  }

  private void transitionInternal(
      ServiceOrderId id, String targetCode, String notes, String reason, UserId changedBy) {
    ServiceOrder os =
        orders.findById(id.value()).orElseThrow(() -> new ServiceOrderNotFoundException(id));
    WorkflowStatus current =
        statuses
            .findById(os.getStatusId())
            .orElseThrow(() -> new WorkflowStatusNotFoundException(os.getStatusId().toString()));
    WorkflowStatus target =
        statuses
            .findByCode(targetCode)
            .orElseThrow(() -> new WorkflowStatusNotFoundException(targetCode));

    if (current.getId().equals(target.getId())) {
      log.debug("OS {} já está em {} — no-op", id, targetCode);
      return;
    }

    boolean allowed =
        transitions.existsByIdFromStatusIdAndIdToStatusId(current.getId(), target.getId());
    if (!allowed) {
      throw new InvalidWorkflowTransitionException(current.getCode(), target.getCode());
    }

    os.applyTransition(target.typedId(), target.getCode(), reason);
    orders.saveAndFlush(os);

    history.save(
        StatusHistory.record(
            os.typedId(),
            current.typedId(),
            target.typedId(),
            changedBy,
            reason != null ? reason : notes));

    Instant now = Instant.now();
    events.publishEvent(
        new OsStatusChangedEvent(
            os.typedId(),
            currentTenantId(),
            current.typedId(),
            current.getCode(),
            target.typedId(),
            target.getCode(),
            changedBy,
            reason != null ? reason : notes,
            now));

    if ("CANCELLED".equals(target.getCode())) {
      events.publishEvent(
          new OsCancelledEvent(os.typedId(), currentTenantId(), reason, changedBy, now));
    } else if ("CLOSED".equals(target.getCode())) {
      events.publishEvent(new OsClosedEvent(os.typedId(), currentTenantId(), changedBy, now));
    }

    log.info(
        "OS transição osId={} {} → {} changedBy={}",
        id,
        current.getCode(),
        target.getCode(),
        changedBy);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
