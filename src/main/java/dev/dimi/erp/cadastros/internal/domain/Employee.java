package dev.dimi.erp.cadastros.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.EmployeeId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Vínculo de funcionário ({@code person + branch + cargo}). Mesma pessoa pode estar ativa em N
 * filiais, mas não duas vezes na mesma (RN-CAD-010, garantido por índice parcial em {@code
 * (person_id, branch_id) WHERE terminated_at IS NULL}).
 */
@Entity
@Table(name = "employee")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "personId", "branchId", "active"})
public class Employee {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "person_id", nullable = false, updatable = false)
  private UUID personId;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Column(name = "job_title_id")
  private UUID jobTitleId;

  @Column(name = "department_id")
  private UUID departmentId;

  @Column(name = "salary", precision = 15, scale = 2)
  private BigDecimal salary;

  @Column(name = "hired_at", nullable = false)
  private LocalDate hiredAt;

  @Column(name = "terminated_at")
  private LocalDate terminatedAt;

  @Column(name = "is_active", nullable = false)
  private boolean active;

  @Column(name = "notes")
  private String notes;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public EmployeeId id() {
    return new EmployeeId(id);
  }

  // ----------------------------------------------------------------------------
  // Comportamento (slice 3.3)
  // ----------------------------------------------------------------------------

  /**
   * Contrata um novo funcionário em uma filial. {@code hiredAt} obrigatório; {@code terminatedAt}
   * fica nulo até desligamento. Service garante RN-CAD-010 (único ativo por person+branch).
   */
  public static Employee hire(
      UUID personId,
      UUID branchId,
      UUID jobTitleId,
      UUID departmentId,
      java.math.BigDecimal salary,
      LocalDate hiredAt,
      String notes) {
    Employee e = new Employee();
    e.id = UuidCreator.getTimeOrderedEpoch();
    e.personId = Objects.requireNonNull(personId, "personId");
    e.branchId = Objects.requireNonNull(branchId, "branchId");
    e.jobTitleId = jobTitleId;
    e.departmentId = departmentId;
    e.salary = salary;
    e.hiredAt = Objects.requireNonNull(hiredAt, "hiredAt");
    e.notes = notes;
    e.active = true;
    return e;
  }

  /** Aplica alterações parciais. PersonId/BranchId/HiredAt são imutáveis após contratação. */
  public void update(
      UUID jobTitleId, UUID departmentId, java.math.BigDecimal salary, String notes) {
    if (jobTitleId != null) this.jobTitleId = jobTitleId;
    if (departmentId != null) this.departmentId = departmentId;
    if (salary != null) this.salary = salary;
    if (notes != null) this.notes = notes;
  }

  /**
   * Desliga o funcionário. {@code terminatedAt} dispara o partial index a liberar o vínculo
   * person+branch para nova contratação (RN-CAD-010).
   */
  public void terminate(LocalDate terminatedAt) {
    this.terminatedAt = Objects.requireNonNull(terminatedAt, "terminatedAt");
    this.active = false;
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
    updatedAt = now;
  }

  @PreUpdate
  void onUpdate() {
    updatedAt = Instant.now();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof Employee other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
