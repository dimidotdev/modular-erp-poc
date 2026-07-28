package dev.dimi.erp.fiscal.internal.domain;

import com.github.f4b6a3.uuid.UuidCreator;
import dev.dimi.erp.cadastros.BranchId;
import dev.dimi.erp.fiscal.FiscalModel;
import dev.dimi.erp.fiscal.InvoiceSeriesId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * Numeração sequencial por filial+modelo+série (RN-FIS-002). Pré-requisito atômico de qualquer
 * emissão fiscal. O incremento de {@link #nextNumber} deve ocorrer sob lock pessimista no caller
 * para garantir unicidade do número emitido por (branch, model, series).
 */
@Entity
@Table(name = "invoice_series")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@ToString(of = {"id", "branchId", "model", "series", "nextNumber"})
public class InvoiceSeries {

  @Id
  @Column(name = "id", nullable = false, updatable = false)
  private UUID id;

  @Column(name = "branch_id", nullable = false, updatable = false)
  private UUID branchId;

  @Enumerated(EnumType.STRING)
  @Column(name = "model", nullable = false, updatable = false)
  private FiscalModel model;

  @Column(name = "series", nullable = false, updatable = false)
  private String series;

  @Column(name = "next_number", nullable = false)
  private long nextNumber;

  @Column(name = "is_active", nullable = false)
  private boolean isActive;

  @Column(name = "created_at", nullable = false, updatable = false)
  private Instant createdAt;

  @Column(name = "updated_at", nullable = false)
  private Instant updatedAt;

  public InvoiceSeriesId id() {
    return new InvoiceSeriesId(id);
  }

  public BranchId branchIdTyped() {
    return new BranchId(branchId);
  }

  public static InvoiceSeries create(UUID branchId, FiscalModel model, String series) {
    InvoiceSeries s = new InvoiceSeries();
    s.id = UuidCreator.getTimeOrderedEpoch();
    s.branchId = Objects.requireNonNull(branchId, "branchId");
    s.model = Objects.requireNonNull(model, "model");
    s.series = Objects.requireNonNull(series, "series");
    s.nextNumber = 1L;
    s.isActive = true;
    return s;
  }

  /**
   * Reserva e retorna o próximo número da série, incrementando {@link #nextNumber} em 1. Caller é
   * responsável por adquirir lock pessimista antes de invocar para evitar duplicidade.
   */
  public long allocateNextNumber() {
    long allocated = this.nextNumber;
    this.nextNumber = allocated + 1L;
    return allocated;
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
    if (!(o instanceof InvoiceSeries other)) return false;
    return id != null && id.equals(other.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
