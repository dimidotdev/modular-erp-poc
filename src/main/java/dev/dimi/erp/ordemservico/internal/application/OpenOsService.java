package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.OsOpenedEvent;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.WorkflowStatusNotFoundException;
import dev.dimi.erp.ordemservico.internal.domain.ServiceOrder;
import dev.dimi.erp.ordemservico.internal.domain.StatusHistory;
import dev.dimi.erp.ordemservico.internal.domain.WorkflowStatus;
import dev.dimi.erp.ordemservico.internal.persistence.ServiceOrderRepository;
import dev.dimi.erp.ordemservico.internal.persistence.StatusHistoryRepository;
import dev.dimi.erp.ordemservico.internal.persistence.WorkflowStatusRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Abre uma OS no status inicial ({@code OPEN} no seed default). Sequencial por filial via {@link
 * OsNumberSequencer}; race no {@code UNIQUE(branch_id, number)} dispara retry até {@value
 * #MAX_NUMBER_RETRIES} vezes.
 */
@Service
public class OpenOsService {

  private static final Logger log = LoggerFactory.getLogger(OpenOsService.class);
  private static final int MAX_NUMBER_RETRIES = 3;
  private static final String INITIAL_STATUS_CODE = "OPEN";

  private final ServiceOrderRepository orders;
  private final WorkflowStatusRepository statuses;
  private final StatusHistoryRepository history;
  private final OsNumberSequencer sequencer;
  private final ApplicationEventPublisher events;

  public OpenOsService(
      ServiceOrderRepository orders,
      WorkflowStatusRepository statuses,
      StatusHistoryRepository history,
      OsNumberSequencer sequencer,
      ApplicationEventPublisher events) {
    this.orders = orders;
    this.statuses = statuses;
    this.history = history;
    this.sequencer = sequencer;
    this.events = events;
  }

  @Transactional
  public ServiceOrderId open(OpenOsCommand cmd, UserId createdBy) {
    WorkflowStatus initial =
        statuses
            .findByCode(INITIAL_STATUS_CODE)
            .orElseThrow(() -> new WorkflowStatusNotFoundException(INITIAL_STATUS_CODE));

    ServiceOrder saved = openWithRetry(cmd, createdBy, initial);

    history.save(StatusHistory.record(saved.typedId(), null, initial.typedId(), createdBy, null));

    events.publishEvent(
        new OsOpenedEvent(
            saved.typedId(),
            currentTenantId(),
            saved.branchIdTyped(),
            saved.customerIdTyped(),
            saved.getNumber(),
            saved.getPriority(),
            createdBy,
            saved.getAssignedTo(),
            Instant.now()));

    log.info(
        "OS aberta osId={} number={} branchId={} createdBy={}",
        saved.typedId(),
        saved.getNumber(),
        saved.branchIdTyped(),
        createdBy);

    return saved.typedId();
  }

  private ServiceOrder openWithRetry(OpenOsCommand cmd, UserId createdBy, WorkflowStatus initial) {
    int attempts = 0;
    while (true) {
      attempts++;
      try {
        long number = sequencer.nextFor(cmd.branchId().value());
        ServiceOrder os =
            ServiceOrder.open(
                number,
                cmd.branchId(),
                cmd.customerId(),
                cmd.contactId(),
                initial.typedId(),
                cmd.priority(),
                createdBy,
                cmd.assignedTo(),
                cmd.paymentConditionId(),
                cmd.problemReported(),
                cmd.equipmentDescription(),
                cmd.serialNumber(),
                cmd.notes());
        return orders.saveAndFlush(os);
      } catch (DataIntegrityViolationException e) {
        if (attempts >= MAX_NUMBER_RETRIES) {
          throw e;
        }
        log.warn(
            "OS number race attempt={} branchId={} — retentando", attempts, cmd.branchId().value());
      }
    }
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
