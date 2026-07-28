package dev.dimi.erp.ordemservico.internal.application;

import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.LaborAppointedEvent;
import dev.dimi.erp.ordemservico.LaborFinishedEvent;
import dev.dimi.erp.ordemservico.OsItemNotFoundException;
import dev.dimi.erp.ordemservico.OsLaborId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.ServiceOrderNotFoundException;
import dev.dimi.erp.ordemservico.internal.domain.OsLabor;
import dev.dimi.erp.ordemservico.internal.domain.ServiceOrder;
import dev.dimi.erp.ordemservico.internal.persistence.OsLaborRepository;
import dev.dimi.erp.ordemservico.internal.persistence.ServiceOrderRepository;
import dev.dimi.erp.shared.multitenancy.TenantContext;
import dev.dimi.erp.shared.multitenancy.TenantId;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Operações de apontamento de mão-de-obra na OS: {@link #start} (clock-in) e {@link #finish}
 * (clock-out). Labor é "fora-de-banda" — não altera totais financeiros da OS (custos internos são
 * gerenciais, não componentes do total faturável); por isso este service não recomputa nada.
 *
 * <p>Idempotência: finish é protegido pela própria entidade ({@code
 * LaborAlreadyFinishedException}).
 */
@Service
public class AppointLaborService {

  private static final Logger log = LoggerFactory.getLogger(AppointLaborService.class);

  private final ServiceOrderRepository orders;
  private final OsLaborRepository labors;
  private final ApplicationEventPublisher events;

  public AppointLaborService(
      ServiceOrderRepository orders, OsLaborRepository labors, ApplicationEventPublisher events) {
    this.orders = orders;
    this.labors = labors;
    this.events = events;
  }

  @Transactional
  public OsLaborId start(
      ServiceOrderId osId,
      EmployeeId employeeId,
      String description,
      boolean isBillable,
      Integer relatedServiceLine,
      UserId actor) {
    ServiceOrder os =
        orders.findById(osId.value()).orElseThrow(() -> new ServiceOrderNotFoundException(osId));

    OsLabor labor =
        OsLabor.start(os.typedId(), employeeId, description, isBillable, relatedServiceLine);
    OsLabor saved = labors.saveAndFlush(labor);

    events.publishEvent(
        new LaborAppointedEvent(
            saved.typedId(),
            os.typedId(),
            currentTenantId(),
            saved.employeeIdTyped(),
            saved.getStartedAt(),
            actor,
            Instant.now()));

    log.info(
        "OS labor iniciado osId={} laborId={} employeeId={} actor={}",
        os.typedId(),
        saved.typedId(),
        employeeId,
        actor);

    return saved.typedId();
  }

  @Transactional
  public void finish(OsLaborId laborId, UserId actor) {
    OsLabor labor =
        labors
            .findById(laborId.value())
            .orElseThrow(() -> new OsItemNotFoundException("labor", laborId.value()));

    labor.finish();
    OsLabor saved = labors.saveAndFlush(labor);

    events.publishEvent(
        new LaborFinishedEvent(
            saved.typedId(),
            saved.osIdTyped(),
            currentTenantId(),
            saved.getHours(),
            saved.getFinishedAt(),
            actor,
            Instant.now()));

    log.info(
        "OS labor finalizado laborId={} osId={} hours={} actor={}",
        saved.typedId(),
        saved.osIdTyped(),
        saved.getHours(),
        actor);
  }

  private TenantId currentTenantId() {
    return new TenantId(TenantContext.currentOrThrow().id());
  }
}
