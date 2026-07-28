package dev.dimi.erp.ordemservico.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.EmployeeId;
import dev.dimi.erp.ordemservico.LaborAlreadyFinishedException;
import dev.dimi.erp.ordemservico.LaborInvalidTransitionException;
import dev.dimi.erp.ordemservico.OsLaborId;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Apontamento de mão-de-obra em uma OS. Espelha {@code service_order_labor}.
 *
 * <p>Ciclo de vida:
 *
 * <ol>
 *   <li><b>Início (clock-in)</b> via {@link #start}: {@code startedAt = now}, {@code finishedAt =
 *       null}, {@code hours = null}.
 *   <li><b>Fim (clock-out)</b> via {@link #finish}: seta {@code finishedAt = now} e calcula {@code
 *       hours = Duration.between(startedAt, finishedAt).toMinutes() / 60.0} com scale=2 {@code
 *       HALF_UP}. Idempotência: segunda chamada lança {@link LaborAlreadyFinishedException}.
 * </ol>
 *
 * <p>{@code relatedServiceLine} é opcional — quando preenchido, aponta para {@code
 * service_order_service.line_number} dentro da mesma OS, permitindo relatórios "horas por serviço".
 * v1 não impõe FK lógica via JPA — slice 9.2b ou relatórios validam.
 */
@Entity
@Table(name = "service_order_labor")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "osId", "employeeId", "startedAt", "finishedAt", "hours"})
public class OsLabor {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "os_id", nullable = false, updatable = false)
  private UUID osId;

  @Column(name = "employee_id", nullable = false, updatable = false)
  private UUID employeeId;

  @Column(name = "started_at", nullable = false, updatable = false)
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "hours", precision = 7, scale = 2)
  private BigDecimal hours;

  @Setter
  @Column(name = "description")
  private String description;

  @Setter
  @Column(name = "internal_cost", precision = 15, scale = 2)
  private BigDecimal internalCost;

  @Column(name = "is_billable", nullable = false)
  private boolean isBillable;

  @Setter
  @Column(name = "related_service_line")
  private Integer relatedServiceLine;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  public OsLaborId typedId() {
    return new OsLaborId(id);
  }

  public ServiceOrderId osIdTyped() {
    return new ServiceOrderId(osId);
  }

  public EmployeeId employeeIdTyped() {
    return new EmployeeId(employeeId);
  }

  /** Clock-in: cria apontamento com {@code startedAt = now} e {@code finishedAt = null}. */
  public static OsLabor start(
      ServiceOrderId osId,
      EmployeeId employeeId,
      String description,
      boolean isBillable,
      Integer relatedServiceLine) {
    OsLabor l = new OsLabor();
    l.id = UuidCreator.getTimeOrderedEpoch();
    l.osId = Objects.requireNonNull(osId, "osId").value();
    l.employeeId = Objects.requireNonNull(employeeId, "employeeId").value();
    l.startedAt = Instant.now();
    l.finishedAt = null;
    l.hours = null;
    l.description = description;
    l.isBillable = isBillable;
    l.relatedServiceLine = relatedServiceLine;
    return l;
  }

  /**
   * Clock-out: seta {@code finishedAt = now} e calcula {@code hours}. Lança {@link
   * LaborAlreadyFinishedException} se já finalizado; lança {@link LaborInvalidTransitionException}
   * se o {@code now} computado vier antes de {@code startedAt} (relógio puxado para trás, paranoia
   * defensiva).
   */
  public void finish() {
    if (this.finishedAt != null) {
      throw new LaborAlreadyFinishedException(typedId());
    }
    Instant now = Instant.now();
    if (now.isBefore(this.startedAt)) {
      throw new LaborInvalidTransitionException(this.startedAt, now);
    }
    this.finishedAt = now;
    long minutes = Duration.between(this.startedAt, this.finishedAt).toMinutes();
    this.hours =
        BigDecimal.valueOf(minutes).divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
  }

  @PrePersist
  void onPersist() {
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (createdAt == null) {
      createdAt = Instant.now();
    }
    if (startedAt == null) {
      startedAt = Instant.now();
    }
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof OsLabor other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
