package dev.dimi.erp.ordemservico.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.cadastros.PersonId;
import dev.dimi.erp.identidade.UserId;
import dev.dimi.erp.ordemservico.OsPriority;
import dev.dimi.erp.ordemservico.ServiceOrderId;
import dev.dimi.erp.ordemservico.WorkflowStatusId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Ordem de Serviço. Slice 9.1: campos descritivos, status, datas. Totais permanecem em zero
 * (preparados para 9.2 quando itens entrarem). {@link #version} já presente para evitar ALTER
 * futuro — transições simultâneas via API podem corromper status sem optimistic locking.
 */
@Entity
@Table(name = "service_order")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "number", "branchId", "statusId"})
public class ServiceOrder {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Version
  @Column(name = "version", nullable = false)
  private long version;

  @Column(name = "number", nullable = false, updatable = false)
  private long number;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "customer_id", nullable = false)
  private UUID customerId;

  @Column(name = "contact_id")
  private UUID contactId;

  @Setter
  @Column(name = "equipment_description")
  private String equipmentDescription;

  @Setter
  @Column(name = "serial_number")
  private String serialNumber;

  @Setter
  @Column(name = "problem_reported")
  private String problemReported;

  @Setter
  @Column(name = "diagnosis")
  private String diagnosis;

  @Setter
  @Column(name = "solution")
  private String solution;

  @Column(name = "status_id", nullable = false)
  private UUID statusId;

  @Enumerated(EnumType.STRING)
  @Column(name = "priority", nullable = false)
  private OsPriority priority;

  @Column(name = "opened_at", nullable = false, updatable = false)
  private Instant openedAt;

  @Column(name = "started_at")
  private Instant startedAt;

  @Column(name = "finished_at")
  private Instant finishedAt;

  @Column(name = "billed_at")
  private Instant billedAt;

  @Column(name = "closed_at")
  private Instant closedAt;

  @Column(name = "cancelled_at")
  private Instant cancelledAt;

  @Column(name = "cancellation_reason")
  private String cancellationReason;

  @Column(name = "payment_condition_id")
  private UUID paymentConditionId;

  @Column(name = "total_products", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalProducts;

  @Column(name = "total_services", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalServices;

  @Column(name = "total_discount", nullable = false, precision = 15, scale = 2)
  private BigDecimal totalDiscount;

  @Column(name = "total", nullable = false, precision = 15, scale = 2)
  private BigDecimal total;

  @Setter
  @Column(name = "notes")
  private String notes;

  @Column(name = "created_by", nullable = false, updatable = false)
  private UUID createdBy;

  @Setter
  @Column(name = "assigned_to")
  private UUID assignedTo;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public ServiceOrderId typedId() {
    return new ServiceOrderId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public PersonId customerIdTyped() {
    return new PersonId(customerId);
  }

  public WorkflowStatusId statusIdTyped() {
    return new WorkflowStatusId(statusId);
  }

  public static ServiceOrder open(
      long number,
      BranchId branchId,
      PersonId customerId,
      UUID contactId,
      WorkflowStatusId initialStatusId,
      OsPriority priority,
      UserId createdBy,
      UUID assignedTo,
      UUID paymentConditionId,
      String problemReported,
      String equipmentDescription,
      String serialNumber,
      String notes) {
    ServiceOrder os = new ServiceOrder();
    os.id = UuidCreator.getTimeOrderedEpoch();
    os.number = number;
    os.branchId = Objects.requireNonNull(branchId, "branchId").value();
    os.customerId = Objects.requireNonNull(customerId, "customerId").value();
    os.contactId = contactId;
    os.statusId = Objects.requireNonNull(initialStatusId, "initialStatusId").value();
    os.priority = priority == null ? OsPriority.NORMAL : priority;
    os.openedAt = Instant.now();
    os.createdBy = Objects.requireNonNull(createdBy, "createdBy").value();
    os.assignedTo = assignedTo;
    os.paymentConditionId = paymentConditionId;
    os.problemReported = problemReported;
    os.equipmentDescription = equipmentDescription;
    os.serialNumber = serialNumber;
    os.notes = notes;
    os.totalProducts = BigDecimal.ZERO;
    os.totalServices = BigDecimal.ZERO;
    os.totalDiscount = BigDecimal.ZERO;
    os.total = BigDecimal.ZERO;
    return os;
  }

  /**
   * Aplica transição: muda status + atualiza timestamps derivados ({@code startedAt}, {@code
   * finishedAt}, {@code billedAt}, {@code closedAt}, {@code cancelledAt}) conforme {@code newCode}.
   * Validação de transição permitida fica no service (consulta {@code os_workflow_transition}).
   */
  public void applyTransition(WorkflowStatusId newStatusId, String newCode, String reason) {
    this.statusId = newStatusId.value();
    Instant now = Instant.now();
    switch (newCode) {
      case "IN_PROGRESS" -> {
        if (this.startedAt == null) this.startedAt = now;
      }
      case "READY" -> this.finishedAt = now;
      case "BILLED" -> this.billedAt = now;
      case "CLOSED" -> this.closedAt = now;
      case "CANCELLED" -> {
        this.cancelledAt = now;
        this.cancellationReason = reason;
      }
      default -> {
        /* status custom — sem timestamp derivado */
      }
    }
  }

  /**
   * Recomputa os quatro totais derivados a partir dos {@code net_value} agregados das tabelas-filho
   * ({@code service_order_product}, {@code service_order_service}). {@code total = totalProducts +
   * totalServices - totalDiscount}. Caller (services de application) é responsável por somar os
   * itens e invocar — entidade não vai ao repo. Todos os valores são normalizados para scale=2
   * HALF_UP; {@code null} é tratado como {@link BigDecimal#ZERO} (chamadas iniciais antes de
   * qualquer item).
   */
  public void recomputeTotals(
      BigDecimal totalProducts, BigDecimal totalServices, BigDecimal totalDiscount) {
    BigDecimal p = totalProducts == null ? BigDecimal.ZERO : totalProducts;
    BigDecimal s = totalServices == null ? BigDecimal.ZERO : totalServices;
    BigDecimal d = totalDiscount == null ? BigDecimal.ZERO : totalDiscount;
    this.totalProducts = p.setScale(2, java.math.RoundingMode.HALF_UP);
    this.totalServices = s.setScale(2, java.math.RoundingMode.HALF_UP);
    this.totalDiscount = d.setScale(2, java.math.RoundingMode.HALF_UP);
    this.total = p.add(s).subtract(d).setScale(2, java.math.RoundingMode.HALF_UP);
  }

  public void setPriority(OsPriority priority) {
    this.priority = Objects.requireNonNull(priority, "priority");
  }

  public void setCustomerId(PersonId customerId) {
    this.customerId = Objects.requireNonNull(customerId, "customerId").value();
  }

  public void setContactId(UUID contactId) {
    this.contactId = contactId;
  }

  public void setPaymentConditionId(UUID paymentConditionId) {
    this.paymentConditionId = paymentConditionId;
  }

  @PrePersist
  void onPersist() {
    Instant now = Instant.now();
    if (id == null) {
      id = UuidCreator.getTimeOrderedEpoch();
    }
    if (createdAt == null) {
      createdAt = now;
    }
    if (openedAt == null) {
      openedAt = now;
    }
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ServiceOrder other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
